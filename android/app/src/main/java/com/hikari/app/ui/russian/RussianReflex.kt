package com.hikari.app.ui.russian

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.viewModelScope
import com.hikari.app.domain.russian.RuAnswerCheck
import com.hikari.app.domain.russian.RuPatternLogic
import com.hikari.app.domain.russian.RuPhrase
import com.hikari.app.domain.russian.RuSessionResult
import com.hikari.app.domain.russian.RussianCourseRepository
import com.hikari.app.domain.russian.RussianPhonetics
import com.hikari.app.domain.russian.RussianProgressStore
import com.hikari.app.domain.russian.apply
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Einzelne Aufgabe für den „Kombi-Meister“ (dynamisch aus Grammatik-Mustern + gelernten
 * Satzbausteinen zusammengesetzt, damit das Gehirn live konstruiert statt auswendig abzulesen).
 */
data class RuKombiChallenge(
    val id: String,
    val title: String,
    val formulaHint: String,
    val germanPrompt: String,
    val russianTarget: String,
    val russianPlain: String,
    val buildingBlocksDe: List<String>,
    val buildingBlocksRu: List<String>,
    val audioClip: String? = null,
    val sourceIds: List<String> = emptyList(),
)

@HiltViewModel
class RussianActiveRecallViewModel @Inject constructor(
    private val repo: RussianCourseRepository,
    private val store: RussianProgressStore,
    private val testSelection: com.hikari.app.domain.russian.RuTestSelection,
    val audio: RussianAudioPlayer,
) : ViewModel() {

    init {
        viewModelScope.launch { audio.warmUp() }
    }

    val progress = store.state
    private val index = repo.index
    private val settings get() = store.state.value.settings

    /**
     * Liefert die Sätze für das aktive Blind-Sprechtraining („Sprech-Reflex“ & „Blind-Coach“).
     * WICHTIG: Auch Sätze, die schon „sitzen“ (Box ≥ 3), werden niemals übersprungen!
     * Jede Runde mischt gezielt:
     *  - ca. 45 % fällige & wackelige Sätze (Box 1–2)
     *  - ca. 35 % bereits sichere Sätze („Sitzt“, Box 3–6), damit sie durch Wiederholung aktiv bleiben
     *  - ca. 20 % eigene Sätze („Meine Sätze“) & frische Sätze
     */
    fun buildReflexQueue(count: Int = 8): List<RuPhrase> {
        val rng = Random(System.nanoTime())
        val state = store.state.value
        val myPhrases = state.mySentences.map {
            com.hikari.app.domain.russian.RuDeepdiveLogic.asPhrase(it.ru, it.de, it.note.ifBlank { "Eigener Satz" })
        }
        val myById = myPhrases.associateBy { it.id }

        val selectedIds = testSelection.ids
        if (selectedIds.isNotEmpty()) {
            testSelection.ids = emptyList()
            val targetCount = count.coerceAtLeast(10)
            val resolved = selectedIds.mapNotNull { id -> repo.phrase(id, settings) ?: myById[id] }.shuffled(rng)
            if (resolved.isNotEmpty()) {
                val weakOrNew = resolved.filter { (state.cards[it.id]?.box ?: 0) <= 2 }
                val mastered = resolved.filter { (state.cards[it.id]?.box ?: 0) >= 3 }
                // Selbst bei vielen wackeligen Sätzen immer auch bereits sichere Sätze („Sitzt“) einstreuen!
                val weakQuota = (targetCount * 0.6).toInt().coerceAtLeast(1)
                val masteredQuota = (targetCount - weakQuota).coerceAtLeast(3)
                return (weakOrNew.take(weakQuota) + mastered.take(masteredQuota) + resolved)
                    .distinctBy { it.id }
                    .shuffled(rng)
                    .take(targetCount)
            }
        }

        val today = store.today()
        val unlocked = state.unlockedDay(index.days.size)
        val dueIds = com.hikari.app.domain.russian.RuSrs.dueIds(state.cards, today)
        val weakIds = state.cards.entries.filter { it.value.box <= 2 }.map { it.key }
        val masteredIds = state.cards.entries.filter { it.value.box >= 3 }.map { it.key }

        val dueAndWeak = (dueIds.shuffled(rng) + weakIds.shuffled(rng))
            .distinct()
            .mapNotNull { repo.phrase(it, settings) }
        val masteredPhrases = masteredIds.shuffled(rng)
            .mapNotNull { repo.phrase(it, settings) }
        val customAndFresh = (myPhrases.shuffled(rng).take(2) + index.phrasesUpTo(unlocked, settings).shuffled(rng))
            .distinctBy { it.id }

        val weakQuota = (count * 0.45).toInt().coerceAtLeast(2)
        val masteredQuota = (count * 0.35).toInt().coerceAtLeast(2)

        val mix = (dueAndWeak.take(weakQuota) + masteredPhrases.take(masteredQuota) + customAndFresh)
            .distinctBy { it.id }
            .take(count)
            .shuffled(rng)

        return mix
    }

    /**
     * Erzeugt abwechslungsreiche Kombi-Aufgaben („Kombi-Meister“) nach der Michel-Thomas- /
     * Natulang-Methode:
     * Nutzt gezielt auch bereits gelernte Sätze aus dem Satzbuch (insbesondere solche, die
     * schon „sitzen“!) und baut sie in NEUE Sätze, Begründungen, Meinungen und Gegenfragen ein,
     * damit du Gelerntes flexibel im Kopf neu kombinierst und Dinge frei erklären kannst.
     */
    fun buildKombiChallenges(count: Int = 8): List<RuKombiChallenge> {
        val rng = Random(System.nanoTime())
        val state = store.state.value
        val gender = settings.gender
        val results = mutableListOf<RuKombiChallenge>()

        // Alle gelernten Sätze aus dem Satzbuch (bevorzugt Sätze, die schon geübt wurden / „sitzen“)
        val selectedIds = testSelection.ids.also { testSelection.ids = emptyList() }
        val learnedIds = if (selectedIds.isNotEmpty()) {
            selectedIds
        } else {
            val mastered = state.cards.entries.filter { it.value.box >= 3 }.map { it.key }.shuffled(rng)
            val practiced = state.cards.keys.shuffled(rng)
            (mastered + practiced).distinct()
        }
        val unlocked = state.unlockedDay(index.days.size)
        val basePhrases = (learnedIds.mapNotNull { repo.phrase(it, settings) } + index.phrasesUpTo(unlocked, settings).shuffled(rng))
            .distinctBy { it.id }

        val statements = basePhrases.filter { it.wordCount in 2..7 && !it.plain.trim().endsWith("?") && !it.plain.trim().endsWith("!") }
        val questions = basePhrases.filter { it.wordCount in 2..7 && it.plain.trim().endsWith("?") }
        val shortReactions = basePhrases.filter { it.wordCount in 1..2 && !it.plain.trim().endsWith("?") }

        // 1. SATZBUCH-TRANSFER: Bereits gelernte Sätze (auch was schon „sitzt“) in neuem Gesprächsrahmen verwenden!
        val connectors = listOf(
            Triple("Честно говоря", "Ч+естно говор+я", "Ehrlich gesagt"),
            Triple("На самом деле", "На с+амом д+еле", "In Wirklichkeit"),
            Triple("Я думаю, что", "Я д+умаю, что", "Ich denke, dass"),
            Triple("Для меня главное", "Для мен+я гл+авное —", "Für mich ist das Wichtigste:"),
        )
        statements.shuffled(rng).take(3).forEachIndexed { idx, st ->
            val conn = connectors[(idx + rng.nextInt(connectors.size)) % connectors.size]
            val lowerRu = st.ru.replaceFirstChar { c -> c.lowercase() }
            val combinedDe = "${conn.third}: ${st.de}"
            val combinedRu = "${conn.second}, $lowerRu"
            results.add(
                RuKombiChallenge(
                    id = "transfer_${st.id}_$idx",
                    title = "Gelerntes neu einbetten (Satzbuch-Transfer)",
                    formulaHint = "${RussianPhonetics.display(conn.second)} + ${st.display.substringBefore(" ")} …",
                    germanPrompt = combinedDe,
                    russianTarget = combinedRu,
                    russianPlain = "${conn.first} ${st.plain}",
                    buildingBlocksDe = listOf(conn.third, st.de),
                    buildingBlocksRu = listOf(RussianPhonetics.display(conn.second), st.display),
                    audioClip = null,
                    sourceIds = listOf(st.id),
                ),
            )
        }

        // 2. ERKLÄRUNG & GEGENFRAGE IM KOPF BAUEN: Eigene Aussage aus dem Satzbuch + Rückfrage an den Partner
        statements.shuffled(rng).take(2).forEachIndexed { idx, st ->
            val q = questions.firstOrNull { it.id != st.id }
            val followDe = q?.de ?: "Und wie ist es bei dir?"
            val followRu = q?.ru ?: "А к+ак у теб+я?"
            val followPlain = q?.plain ?: "А как у тебя?"
            val combinedDe = "${st.de} — $followDe"
            val combinedRu = "${st.ru} $followRu"
            results.add(
                RuKombiChallenge(
                    id = "explain_ask_${st.id}_$idx",
                    title = "Erklären + Gegenfrage stellen",
                    formulaHint = "${st.display.substringBefore(" ")} … + ${RussianPhonetics.display(followRu).substringBefore(" ")} …?",
                    germanPrompt = combinedDe,
                    russianTarget = combinedRu,
                    russianPlain = "${st.plain} $followPlain",
                    buildingBlocksDe = listOf(st.de, followDe),
                    buildingBlocksRu = listOf(st.display, RussianPhonetics.display(followRu)),
                    audioClip = null,
                    sourceIds = listOfNotNull(st.id, q?.id),
                ),
            )
        }

        // 3. URSACHE & GEGENSATZ („но“ / „потому что“): Zwei gelernte Sätze im Kopf verbinden
        if (statements.size >= 4) {
            val shuffledSt = statements.shuffled(rng)
            val a = shuffledSt[0]
            val b = shuffledSt[1]
            val combinedDe = "${a.de.removeSuffix(".")}, aber ${b.de}"
            val combinedRu = "${a.ru.removeSuffix(".")}, но ${b.ru.replaceFirstChar { it.lowercase() }}"
            results.add(
                RuKombiChallenge(
                    id = "contrast_${a.id}_${b.id}",
                    title = "Zwei Gedanken mit »но« (aber) verbinden",
                    formulaHint = "${a.display.substringBefore(" ")} … + но + ${b.display.substringBefore(" ")} …",
                    germanPrompt = combinedDe,
                    russianTarget = combinedRu,
                    russianPlain = "${a.plain} но ${b.plain}",
                    buildingBlocksDe = listOf(a.de, "aber", b.de),
                    buildingBlocksRu = listOf(a.display, "но", b.display),
                    audioClip = null,
                    sourceIds = listOf(a.id, b.id),
                ),
            )
        }

        // 4. SATZBAU-BAUKASTEN: Aus den 25 Satzbau-Mustern (Woche 1–5) zufällige Slot-Kombinationen würfeln
        for (pattern in repo.patterns.shuffled(rng).take(4)) {
            if (pattern.slots.isEmpty()) continue
            val randomChoices = pattern.slots.map { slot -> rng.nextInt(slot.options.size) }
            val built = RuPatternLogic.build(pattern, randomChoices, gender)
            val blocksDe = pattern.slots.mapIndexed { idx, slot ->
                val opt = slot.options[randomChoices[idx]]
                if (gender == com.hikari.app.domain.russian.RuGender.FEMALE && opt.deF != null) opt.deF else opt.de
            }
            val blocksRu = pattern.slots.mapIndexed { idx, slot ->
                val opt = slot.options[randomChoices[idx]]
                if (gender == com.hikari.app.domain.russian.RuGender.FEMALE && opt.ruF != null) opt.ruF else opt.ru
            }
            results.add(
                RuKombiChallenge(
                    id = "pat_${pattern.id}_${randomChoices.joinToString("_")}",
                    title = pattern.title,
                    formulaHint = pattern.formula.replace("+", ""),
                    germanPrompt = built.de,
                    russianTarget = built.ru,
                    russianPlain = RussianPhonetics.plain(built.ru),
                    buildingBlocksDe = blocksDe,
                    buildingBlocksRu = blocksRu,
                    audioClip = null,
                ),
            )
        }

        // 5. REAKTION + GEDANKE VERBINDEN
        repeat(2) { idx ->
            val r = shortReactions.randomOrNull(rng)
            val s = statements.randomOrNull(rng)?.takeIf { it.id != r?.id }
            if (r != null && s != null) {
                val combinedDe = "${r.de.removeSuffix(".")} — ${s.de}"
                val combinedRu = "${r.ru.removeSuffix(".")}, ${s.ru.replaceFirstChar { c -> c.lowercase() }}"
                results.add(
                    RuKombiChallenge(
                        id = "combo_${r.id}_${s.id}_$idx",
                        title = "Reaktion + Gedanke verbinden",
                        formulaHint = "${r.display} + ${s.display.substringBefore(" ")} …",
                        germanPrompt = combinedDe,
                        russianTarget = combinedRu,
                        russianPlain = "${r.plain} ${s.plain}",
                        buildingBlocksDe = listOf(r.de, s.de),
                        buildingBlocksRu = listOf(r.display, s.display),
                        audioClip = null,
                    ),
                )
            }
        }

        return results.shuffled(rng).take(count)
    }

    fun recordReflexSuccess(phraseId: String, blindFirstTry: Boolean) {
        val today = store.today()
        store.update {
            it.apply(
                RuSessionResult(
                    xp = if (blindFirstTry) 12 else 6,
                    graded = mapOf(phraseId to blindFirstTry),
                    spokenOk = 1,
                ),
                today,
            )
        }
    }

    /**
     * Speichert einen im Kombi-Meister frei zusammengesetzten Satz direkt im Satzbuch
     * unter „Meine Sätze“ UND verbucht die verwendeten Ausgangssätze als erfolgreiche
     * Wiederholung im Satzbuch.
     */
    fun recordKombiSentence(item: RuKombiChallenge) {
        item.sourceIds.forEach { id ->
            store.gradeSingleCard(id, correct = true, xp = 2, spokenOk = 0)
        }
        store.saveMySentence(
            com.hikari.app.domain.russian.RuMySentence(
                ru = item.russianTarget,
                de = item.germanPrompt,
                patternId = item.id,
                note = "Kombi-Meister · ${item.title}",
            ),
        )
    }

    fun finishReflexSession(score: Int): Boolean {
        val prevBest = store.state.value.bestReflex
        store.update { it.copy(bestReflex = maxOf(it.bestReflex, score)) }
        return score > prevBest
    }

    fun finishKombiSession(score: Int): Boolean {
        val prevBest = store.state.value.bestKombi
        val today = store.today()
        store.update {
            it.apply(RuSessionResult(xp = (score / 4).coerceAtLeast(10), graded = emptyMap(), spokenOk = 1), today)
                .copy(bestKombi = maxOf(it.bestKombi, score))
        }
        return score > prevBest
    }

    override fun onCleared() {
        audio.stop()
    }
}

// ── 1. ⚡ SPRECH-REFLEX (Blind-Produktion + 4s-Countdown + 30s-Pimsleur-Schleife) ────

private const val REFLEX_WINDOW_MS = 4_500L

@Composable
fun RussianReflexScreen(
    onClose: () -> Unit,
    vm: RussianActiveRecallViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var started by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var blindStreak by remember { mutableIntStateOf(0) }
    var newBest by remember { mutableStateOf(false) }

    // Dynamische Queue: Sätze, bei denen man spicken musste, werden 2 Plätze später
    // (~30 Sekunden später) automatisch noch einmal blind abgefragt!
    val queue = remember { mutableStateListOf<RuPhrase>().apply { addAll(vm.buildReflexQueue(7)) } }
    var currentIndex by remember { mutableIntStateOf(0) }
    val requeuedIds = remember { mutableStateListOf<String>() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) started = true
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        RuSessionTopBar(
            progress = if (!started || queue.isEmpty()) 0f else (if (finished) 1f else currentIndex.toFloat() / queue.size),
            onClose = { vm.audio.stop(); onClose() },
            trailing = if (started && !finished) "$score Pkt" else null,
        )

        when {
            queue.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Starte zuerst Tag 1", color = HikariText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Sobald du die ersten Sätze kennengelernt hast, kannst du hier deinen freien Sprech-Reflex trainieren.",
                    color = HikariTextMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                RuPrimaryButton("Zurück", onClick = onClose)
            }

            !started -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RuOverline("GEGEN DEN SPRECH-BLACKOUT", color = HikariAmber)
                Spacer(Modifier.height(8.dp))
                Text("⚡ Sprech-Reflex", color = HikariText, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Im echten Gespräch hast du keine russischen Untertitel vor Augen.\n\n" +
                        "Hier siehst du NUR den deutschen Gedanken und hast 4,5 Sekunden, um ihn frei aus dem Kopf auf Russisch auszusprechen.\n\n" +
                        "💡 Kommst du nicht drauf? Nutze die 1-Wort-Starthilfe — der Satz kommt dann 30 Sekunden später automatisch noch einmal blind dran, bis er wirklich im Kopf sitzt!",
                    color = HikariTextMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(30.dp))
                RuPrimaryButton("Training starten") {
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
                RuOverline("SPRECH-REFLEX ABGESCHLOSSEN", color = HikariAmber)
                Spacer(Modifier.height(10.dp))
                Text("$score Punkte", color = HikariText, fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (newBest) "🏆 Neuer persönlicher Rekord! Alle Sätze frei aus dem Kopf gesprochen."
                    else "Alle Sätze sitzen jetzt ohne Spickzettel im aktiven Gedächtnis!",
                    color = HikariTextMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                RuPrimaryButton("Neue Runde") {
                    queue.clear()
                    queue.addAll(vm.buildReflexQueue(7))
                    requeuedIds.clear()
                    currentIndex = 0
                    score = 0
                    blindStreak = 0
                    finished = false
                }
                Spacer(Modifier.height(10.dp))
                RuGhostButton("Fertig", Modifier.fillMaxWidth(), onClose)
            }

            else -> {
                val phrase = queue[currentIndex]
                val isRetryRound = phrase.id in requeuedIds
                ReflexRoundView(
                    roundKey = "$currentIndex-${phrase.id}",
                    phrase = phrase,
                    isRetryRound = isRetryRound,
                    blindStreak = blindStreak,
                    vm = vm,
                    onCompleteRound = { blindWithoutHints, fastReflex ->
                        vm.recordReflexSuccess(phrase.id, blindWithoutHints)
                        if (blindWithoutHints) {
                            blindStreak++
                            val pts = (if (fastReflex) 25 else 15) + (blindStreak.coerceAtMost(5) * 3)
                            score += pts
                        } else {
                            blindStreak = 0
                            score += 5
                            // Pimsleur-30s-Schleife: Wenn Hilfe nötig war, 2 Runden später erneut blind abfragen!
                            val insertPos = (currentIndex + 3).coerceAtMost(queue.size)
                            queue.add(insertPos, phrase)
                            requeuedIds.add(phrase.id)
                        }
                        if (currentIndex + 1 < queue.size) {
                            currentIndex++
                        } else {
                            newBest = vm.finishReflexSession(score)
                            finished = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ReflexRoundView(
    roundKey: String,
    phrase: RuPhrase,
    isRetryRound: Boolean,
    blindStreak: Int,
    vm: RussianActiveRecallViewModel,
    onCompleteRound: (blindWithoutHints: Boolean, fastReflex: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val recognizer = remember(roundKey) { RussianSpeechRecognizer(context) }
    DisposableEffect(roundKey) { onDispose { recognizer.destroy() } }
    val state by recognizer.state.collectAsState()
    val playing by vm.audio.playing.collectAsState()

    var timeLeftMs by remember(roundKey) { mutableLongStateOf(REFLEX_WINDOW_MS) }
    var timerRunning by remember(roundKey) { mutableStateOf(true) }
    var spokeWithinReflexWindow by remember(roundKey) { mutableStateOf(false) }

    var hintLevel by remember(roundKey) { mutableIntStateOf(0) } // 0 = 100% blind, 1 = 1. Wort, 2 = Voll-Spick/Audio
    var flashSolution by remember(roundKey) { mutableStateOf(false) }
    var passed by remember(roundKey) { mutableStateOf(false) }
    var speechScore by remember(roundKey) { mutableStateOf<RuAnswerCheck.SpeechScore?>(null) }
    var evaluating by remember(roundKey) { mutableStateOf(false) }

    fun startMic() {
        speechScore = null
        evaluating = false
        recognizer.reset()
        recognizer.start()
    }

    // Mikrofon startet sofort automatisch; 4,5s-Reflex-Timer läuft an
    LaunchedEffect(roundKey) {
        startMic()
        while (timerRunning && timeLeftMs > 0 && !passed) {
            delay(100)
            if (state is RuListenState.Partial) {
                spokeWithinReflexWindow = true
            }
            timeLeftMs = (timeLeftMs - 100).coerceAtLeast(0)
        }
        timerRunning = false
    }

    // Kurzzeit-Spickzettel nach 2,5 Sekunden wieder ausblenden, damit man trotzdem aus dem Kopf sprechen muss!
    LaunchedEffect(flashSolution) {
        if (flashSolution) {
            delay(2_600L)
            if (!passed) {
                flashSolution = false
            }
        }
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is RuListenState.Partial -> {
                if (timeLeftMs > 0) spokeWithinReflexWindow = true
            }
            is RuListenState.Done -> {
                evaluating = false
                val sc = RuAnswerCheck.speech(phrase.plain, s.hypotheses)
                speechScore = sc
                if (sc.passed) {
                    passed = true
                    timerRunning = false
                    // Zur Bestätigung die echte Muttersprachler-Aufnahme abspielen
                    vm.audio.play(phrase.audio)
                }
            }
            is RuListenState.Failed -> {
                evaluating = false
            }
            else -> Unit
        }
    }

    val listening = state is RuListenState.Listening || state is RuListenState.Partial
    val pulse by animateFloatAsState(if (listening) 1.12f else 1f, tween(250), label = "reflex-mic")
    val firstWordDisplay = remember(phrase.display) { phrase.display.trim().substringBefore(" ") }
    val firstWordRu = remember(phrase.ru) { phrase.ru.trim().substringBefore(" ") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RuOverline(
                if (isRetryRound) "🔄 30s-GEDÄCHTNIS-CHECK (OHNE SPICKEN!)" else "⚡ SAG ES FREI AUF RUSSISCH",
                color = if (isRetryRound) HikariAmber else HikariTextMuted,
            )
            if (blindStreak >= 2) {
                Text(
                    "🔥 $blindStreak× blind",
                    color = HikariAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // 4,5-Sekunden Reflex-Balken
        LinearProgressIndicator(
            progress = { (timeLeftMs.toFloat() / REFLEX_WINDOW_MS).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = if (timeLeftMs > 1_500L) HikariAmber else Color(0xFFE57373),
            trackColor = HikariBorderStrong,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                passed && hintLevel == 0 && spokeWithinReflexWindow -> "⚡ Blitz-Reflex! Unter 4,5s frei aus dem Kopf abgerufen!"
                passed && hintLevel == 0 -> "🧠 Stark! Komplett frei ohne Spickzettel gesprochen!"
                passed -> "✅ Gesprochen! (Kommt gleich in 30s noch einmal komplett blind dran)"
                timeLeftMs > 0 -> "Reflex-Zeit: ${(timeLeftMs / 1000f).let { "%.1f".format(it) }}s — sprich sofort los!"
                else -> "Kein Stress — überleg kurz oder nutze die 1-Wort-Starthilfe unten"
            },
            color = if (passed) HikariAmber else HikariTextFaint,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(18.dp))

        // Hauptkarte: Zeigt vor der Lösung NUR den deutschen Gedanken!
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(HikariCardBg)
                .border(
                    1.5.dp,
                    if (passed) Color(0xFF4CAF50) else HikariAmber.copy(alpha = 0.45f),
                    RoundedCornerShape(20.dp),
                )
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Wie sagst du auf Russisch:",
                color = HikariTextFaint,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "„${phrase.de}“",
                color = HikariText,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp,
            )

            // Stufe 1: Nur das allererste Wort als Gedächtnis-Zündfunke
            if (hintLevel == 1 && !passed && !flashSolution) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Starthilfe: $firstWordDisplay …",
                    color = HikariAmber,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    translitAnnotated(firstWordRu),
                    color = HikariTextMuted,
                    fontSize = 14.sp,
                )
            }

            // Stufe 2 (kurzes Einprägen für 2,5s) oder nach erfolgreicher Antwort:
            if (flashSolution || passed) {
                Spacer(Modifier.height(18.dp))
                if (flashSolution && !passed) {
                    Text(
                        "👀 Einprägen (verschwindet sofort wieder):",
                        color = HikariAmber,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                RuPhraseBlock(phrase, cyrillicSize = 26.sp)
                Spacer(Modifier.height(10.dp))
                RuPlayRow(
                    playingNow = playing == phrase.audio,
                    onPlay = { vm.audio.play(phrase.audio) },
                    onSlow = { vm.audio.play(phrase.audio, slow = true) },
                    size = 48.dp,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // Gestufte Gedächtnis-Hilfen (nur solange noch nicht gelöst)
        if (!passed) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(HikariSurfaceHigh)
                        .border(1.dp, HikariBorder, RoundedCornerShape(14.dp))
                        .clickable {
                            if (hintLevel < 1) hintLevel = 1
                        }
                        .padding(vertical = 12.dp, horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lightbulb, null, tint = HikariAmber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("1. Wort zeigen", color = HikariText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(HikariSurfaceHigh)
                        .border(1.dp, HikariBorder, RoundedCornerShape(14.dp))
                        .clickable {
                            hintLevel = 2
                            flashSolution = true
                            recognizer.stopListening()
                            vm.audio.play(phrase.audio) {
                                startMic()
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Visibility, null, tint = HikariTextMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Hören & Spicken", color = HikariTextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Großer Mikrofon-Button
            Box(
                Modifier
                    .size(88.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(if (listening) HikariAmber else Color.White)
                    .clickable(enabled = !evaluating) {
                        if (listening) {
                            evaluating = true
                            recognizer.stopListening()
                        } else {
                            vm.audio.stop()
                            startMic()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (listening) Icons.Outlined.GraphicEq else Icons.Outlined.Mic,
                    contentDescription = "Sprechen",
                    tint = Color.Black,
                    modifier = Modifier.size(38.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when (val s = state) {
                    is RuListenState.Listening -> "Mikrofon aktiv — sprich den russischen Satz laut aus"
                    is RuListenState.Partial -> "»${s.text}«"
                    else -> if (evaluating) "Wird ausgewertet …" else "Tippe aufs Mikrofon und sprich aus dem Kopf"
                },
                color = if (listening) HikariAmber else HikariTextMuted,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center,
            )

            speechScore?.let { sc ->
                Spacer(Modifier.height(12.dp))
                RuSpeechScoreCard(sc, large = true)
            }

            Spacer(Modifier.height(18.dp))
            RuGhostButton("Ich hab's laut gesagt → Weiter", Modifier.fillMaxWidth()) {
                passed = true
                vm.audio.play(phrase.audio)
            }
        } else {
            speechScore?.let { sc ->
                RuSpeechScoreCard(sc, large = true)
                Spacer(Modifier.height(16.dp))
            }
            RuPrimaryButton(
                if (hintLevel == 0) "Stark! Nächster Satz" else "Verstanden (kommt in 30s nochmal blind)",
                onClick = { onCompleteRound(hintLevel == 0, spokeWithinReflexWindow && hintLevel == 0) },
            )
        }
    }
}

// ── 2. 🧩 KOMBI-MEISTER (Live-Satz-Baukasten aus dem Kopf nach Michel Thomas / Natulang) ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RussianKombiScreen(
    onClose: () -> Unit,
    vm: RussianActiveRecallViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val voice = remember { RussianConversationVoice(context) }
    DisposableEffect(Unit) { onDispose { voice.close() } }

    var started by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var newBest by remember { mutableStateOf(false) }

    val challenges = remember { mutableStateListOf<RuKombiChallenge>().apply { addAll(vm.buildKombiChallenges(8)) } }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) started = true
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        RuSessionTopBar(
            progress = if (!started || challenges.isEmpty()) 0f else (if (finished) 1f else round.toFloat() / challenges.size),
            onClose = { voice.stop(); vm.audio.stop(); onClose() },
            trailing = if (started && !finished) "$score Pkt" else null,
        )

        when {
            !started -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RuOverline("MICHEL-THOMAS & NATULANG METHODE", color = HikariAmber)
                Spacer(Modifier.height(8.dp))
                Text("🧩 Kombi-Meister", color = HikariText, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Warum fällt einem im echten Gespräch nichts ein? Weil man im echten Leben selten 1:1 einen auswendig gelernten Satz aufsagt, sondern Bausteine live im Kopf kombinieren muss!\n\n" +
                        "Hier würfelt Hikari deine gelernten Satzmuster und Reaktionen immer wieder neu zusammen — und du kombinierst sie frei aus dem Kopf ins Mikrofon.",
                    color = HikariTextMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(30.dp))
                RuPrimaryButton("Kombi-Training starten") {
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
                RuOverline("KOMBI-TRAINING GESCHAFFT", color = HikariAmber)
                Spacer(Modifier.height(10.dp))
                Text("$score Punkte", color = HikariText, fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (newBest) "🏆 Neuer Kombi-Rekord! Dein Gehirn baut russische Sätze jetzt flexibel zusammen."
                    else "Stark kombiniert! Je öfter du Bausteine frei mischst, desto schneller reagierst du im Gespräch.",
                    color = HikariTextMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                RuPrimaryButton("Neue Kombis würfeln") {
                    challenges.clear()
                    challenges.addAll(vm.buildKombiChallenges(8))
                    round = 0
                    score = 0
                    finished = false
                }
                Spacer(Modifier.height(10.dp))
                RuGhostButton("Fertig", Modifier.fillMaxWidth(), onClose)
            }

            else -> {
                val item = challenges[round]
                val recognizer = remember(item.id) { RussianSpeechRecognizer(context) }
                DisposableEffect(item.id) { onDispose { recognizer.destroy() } }
                val state by recognizer.state.collectAsState()

                var showRuBlocks by remember(item.id) { mutableStateOf(false) }
                var solved by remember(item.id) { mutableStateOf(false) }
                var speechScore by remember(item.id) { mutableStateOf<RuAnswerCheck.SpeechScore?>(null) }

                fun playTargetAudio() {
                    if (item.audioClip != null) {
                        vm.audio.play(item.audioClip)
                    } else {
                        voice.speak(RussianPhonetics.plain(item.russianTarget), 0.92f)
                    }
                }

                LaunchedEffect(item.id) {
                    recognizer.reset()
                    recognizer.start()
                }

                LaunchedEffect(state) {
                    val s = state
                    if (s is RuListenState.Done) {
                        val sc = RuAnswerCheck.speech(item.russianPlain, s.hypotheses)
                        speechScore = sc
                        if (sc.passed) {
                            solved = true
                            vm.recordKombiSentence(item)
                            playTargetAudio()
                        }
                    }
                }

                val listening = state is RuListenState.Listening || state is RuListenState.Partial
                val pulse by animateFloatAsState(if (listening) 1.12f else 1f, tween(250), label = "kombi-mic")

                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .animateContentSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RuOverline("BAUSTEINE LIVE KOMBINIEREN · ${item.title.uppercase()}", color = HikariAmber)
                    Spacer(Modifier.height(12.dp))

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(HikariCardBg)
                            .border(1.5.dp, if (solved) Color(0xFF4CAF50) else HikariBorderStrong, RoundedCornerShape(20.dp))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Kombiniere auf Russisch:", color = HikariTextFaint, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "„${item.germanPrompt}“",
                            color = HikariText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(14.dp))

                        // Deutsche Baustein-Kapseln (und optional russische Baustein-Stütze)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val blocks = if (showRuBlocks || solved) item.buildingBlocksRu else item.buildingBlocksDe
                            blocks.forEachIndexed { idx, b ->
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(HikariSurfaceHigh)
                                        .border(1.dp, HikariAmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        "${idx + 1}. $b",
                                        color = if (showRuBlocks || solved) HikariAmber else HikariTextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        if (!solved && !showRuBlocks) {
                            Spacer(Modifier.height(12.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(HikariSurfaceHigh)
                                    .clickable { showRuBlocks = true }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                Text("💡 Russische Bausteine-Schablone zeigen", color = HikariAmber, fontSize = 12.5.sp)
                            }
                        }

                        if (solved) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                RussianPhonetics.display(item.russianTarget),
                                color = HikariAmber,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                translitAnnotated(item.russianTarget),
                                color = HikariTextMuted,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    if (!solved) {
                        Box(
                            Modifier
                                .size(86.dp)
                                .scale(pulse)
                                .clip(CircleShape)
                                .background(if (listening) HikariAmber else Color.White)
                                .clickable {
                                    if (listening) {
                                        recognizer.stopListening()
                                    } else {
                                        voice.stop()
                                        recognizer.reset()
                                        recognizer.start()
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (listening) Icons.Outlined.GraphicEq else Icons.Outlined.Mic,
                                contentDescription = "Sprechen",
                                tint = Color.Black,
                                modifier = Modifier.size(38.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            when (val s = state) {
                                is RuListenState.Listening -> "Sprich den kombinierten Satz laut auf Russisch …"
                                is RuListenState.Partial -> "»${s.text}«"
                                else -> "Tippe aufs Mikrofon, um den kombinierten Satz zu sprechen"
                            },
                            color = if (listening) HikariAmber else HikariTextMuted,
                            fontSize = 13.5.sp,
                            textAlign = TextAlign.Center,
                        )

                        speechScore?.let { sc ->
                            Spacer(Modifier.height(12.dp))
                            RuSpeechScoreCard(sc, large = true)
                        }

                        Spacer(Modifier.height(18.dp))
                        RuGhostButton("Lösung hören & vergleichen", Modifier.fillMaxWidth()) {
                            showRuBlocks = true
                            solved = true
                            playTargetAudio()
                        }
                    } else {
                        speechScore?.let { sc ->
                            RuSpeechScoreCard(sc, large = true)
                            Spacer(Modifier.height(14.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RuGhostButton("🔊 Anhören", Modifier.weight(1f)) { playTargetAudio() }
                            RuPrimaryButton("Weiter", Modifier.weight(1f)) {
                                score += if (showRuBlocks) 10 else 25
                                if (round + 1 < challenges.size) {
                                    round++
                                } else {
                                    newBest = vm.finishKombiSession(score)
                                    finished = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 3. 🎧 BLIND-COACH (Pimsleur Hands-Free Audio-Coach ohne Hinschauen) ─────────────

/**
 * Zweisprachiger Audio-Prompt-Sprecher für den Pimsleur-Blind-Modus:
 * Spricht den deutschen Impuls („Wie sagst du auf Russisch: …“) laut vor,
 * damit der Nutzer das Handy weglegen oder die Augen schließen kann.
 */
private class BlindGermanPromptVoice(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var onDoneCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            main.post {
                if (!closed && status == TextToSpeech.SUCCESS) {
                    val res = tts?.setLanguage(Locale.GERMAN) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    ready = res >= 0
                }
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                main.post {
                    val cb = onDoneCallback
                    onDoneCallback = null
                    cb?.invoke()
                }
            }
            @Deprecated("Android callback")
            override fun onError(utteranceId: String?) {
                main.post {
                    val cb = onDoneCallback
                    onDoneCallback = null
                    cb?.invoke()
                }
            }
        })
    }

    fun speakGermanPrompt(promptText: String, onFinished: () -> Unit) {
        if (closed || !ready) {
            main.postDelayed(onFinished, 1_200L)
            return
        }
        onDoneCallback = onFinished
        tts?.setSpeechRate(1.0f)
        val res = tts?.speak(promptText, TextToSpeech.QUEUE_FLUSH, null, "de-blind-prompt")
        if (res == TextToSpeech.ERROR) {
            onDoneCallback = null
            main.postDelayed(onFinished, 1_200L)
        }
    }

    fun stop() {
        onDoneCallback = null
        tts?.stop()
    }

    fun close() {
        closed = true
        stop()
        tts?.shutdown()
        main.removeCallbacksAndMessages(null)
    }
}

private enum class BlindCoachPhase {
    PROMPTING_GERMAN,
    LISTENING_USER,
    PLAYING_MASTER,
    FEEDBACK_PAUSE,
}

@Composable
fun RussianBlindCoachScreen(
    onClose: () -> Unit,
    vm: RussianActiveRecallViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val deVoice = remember { BlindGermanPromptVoice(context) }
    DisposableEffect(Unit) { onDispose { deVoice.close() } }

    var started by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    var passedCount by remember { mutableIntStateOf(0) }

    val queue = remember { mutableStateListOf<RuPhrase>().apply { addAll(vm.buildReflexQueue(7)) } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) started = true
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        RuSessionTopBar(
            progress = if (!started || queue.isEmpty()) 0f else (if (finished) 1f else round.toFloat() / queue.size),
            onClose = { deVoice.stop(); vm.audio.stop(); onClose() },
        )

        when {
            queue.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Starte zuerst Tag 1", color = HikariText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                RuPrimaryButton("Zurück", onClick = onClose)
            }

            !started -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.Headphones, null, tint = HikariAmber, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(14.dp))
                RuOverline("PIMSLEUR AUDIO-METHODE", color = HikariAmber)
                Spacer(Modifier.height(8.dp))
                Text("🎧 Blind-Coach (Augen zu)", color = HikariText, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Leg das Handy vor dich hin oder schließ die Augen — auf dem Bildschirm wird KEIN russischer Text angezeigt!\n\n" +
                        "1. Der Coach sagt dir auf Deutsch, was du ausdrücken sollst.\n" +
                        "2. Das Mikrofon öffnet sich automatisch und du sprichst den Satz aus deinem Kopf auf Russisch.\n" +
                        "3. Direkt danach hörst du den russischen Muttersprachler zum Vergleich und sprichst ihn noch einmal laut nach.",
                    color = HikariTextMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(30.dp))
                RuPrimaryButton("Audio-Coach starten") {
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
                RuOverline("AUDIO-COACH BEENDET", color = HikariAmber)
                Spacer(Modifier.height(10.dp))
                Text("$passedCount / ${queue.size}", color = HikariText, fontSize = 38.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sätze komplett blind ohne Bildschirm abgerufenen!",
                    color = HikariTextMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                RuPrimaryButton("Noch eine Sitzung") {
                    queue.clear()
                    queue.addAll(vm.buildReflexQueue(7))
                    round = 0
                    passedCount = 0
                    finished = false
                }
                Spacer(Modifier.height(10.dp))
                RuGhostButton("Fertig", Modifier.fillMaxWidth(), onClose)
            }

            else -> {
                val phrase = queue[round]
                val recognizer = remember(round, phrase.id) { RussianSpeechRecognizer(context) }
                DisposableEffect(round, phrase.id) { onDispose { recognizer.destroy() } }
                val state by recognizer.state.collectAsState()

                var phase by remember(round, phrase.id) { mutableStateOf(BlindCoachPhase.PROMPTING_GERMAN) }
                var lastScore by remember(round, phrase.id) { mutableStateOf<RuAnswerCheck.SpeechScore?>(null) }
                var revealText by remember(round, phrase.id) { mutableStateOf(false) }

                LaunchedEffect(round, phrase.id) {
                    phase = BlindCoachPhase.PROMPTING_GERMAN
                    deVoice.speakGermanPrompt("Sag auf Russisch: ${phrase.de}") {
                        phase = BlindCoachPhase.LISTENING_USER
                        recognizer.reset()
                        recognizer.start()
                    }
                }

                LaunchedEffect(state) {
                    val s = state
                    if (phase == BlindCoachPhase.LISTENING_USER && (s is RuListenState.Done || s is RuListenState.Failed)) {
                        val sc = if (s is RuListenState.Done) {
                            RuAnswerCheck.speech(phrase.plain, s.hypotheses)
                        } else {
                            null
                        }
                        lastScore = sc
                        val ok = sc?.passed == true
                        if (ok) {
                            passedCount++
                            vm.recordReflexSuccess(phrase.id, true)
                        } else {
                            // Pimsleur-Wiederholung: Bei Unsicherheit 2 Runden später erneut einplanen
                            if (queue.size < 12) {
                                val insertAt = (round + 3).coerceAtMost(queue.size)
                                queue.add(insertAt, phrase)
                            }
                        }
                        phase = BlindCoachPhase.PLAYING_MASTER
                        vm.audio.play(phrase.audio) {
                            phase = BlindCoachPhase.FEEDBACK_PAUSE
                        }
                    }
                }

                // Nach dem Muttersprachler-Audio 2,2 Sekunden Zeit zum Nachsprechen lassen, dann automatisch weiter!
                LaunchedEffect(phase) {
                    if (phase == BlindCoachPhase.FEEDBACK_PAUSE) {
                        delay(2_200L)
                        if (round + 1 < queue.size) {
                            round++
                        } else {
                            finished = true
                        }
                    }
                }

                val pulse by animateFloatAsState(
                    if (phase == BlindCoachPhase.LISTENING_USER) 1.16f else 1f,
                    tween(300),
                    label = "blind-orb",
                )

                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    RuOverline("AUGEN-ZU-MODUS · RUNDE ${round + 1} VON ${queue.size}", color = HikariAmber)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "„${phrase.de}“",
                        color = HikariText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(36.dp))

                    // Großer pulsierender Audio-Orb (kein russischer Text zum Ablesen!)
                    Box(
                        Modifier
                            .size(132.dp)
                            .scale(pulse)
                            .clip(CircleShape)
                            .background(
                                when (phase) {
                                    BlindCoachPhase.LISTENING_USER -> HikariAmber
                                    BlindCoachPhase.PLAYING_MASTER, BlindCoachPhase.FEEDBACK_PAUSE -> Color(0xFF4CAF50)
                                    else -> HikariSurfaceHigh
                                },
                            )
                            .clickable {
                                if (phase == BlindCoachPhase.LISTENING_USER) {
                                    recognizer.stopListening()
                                } else {
                                    deVoice.stop()
                                    phase = BlindCoachPhase.LISTENING_USER
                                    recognizer.reset()
                                    recognizer.start()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            when (phase) {
                                BlindCoachPhase.LISTENING_USER -> Icons.Outlined.Mic
                                BlindCoachPhase.PLAYING_MASTER, BlindCoachPhase.FEEDBACK_PAUSE -> Icons.Outlined.CheckCircle
                                else -> Icons.Outlined.Headphones
                            },
                            contentDescription = null,
                            tint = if (phase == BlindCoachPhase.PROMPTING_GERMAN) HikariAmber else Color.Black,
                            modifier = Modifier.size(54.dp),
                        )
                    }

                    Spacer(Modifier.height(22.dp))
                    Text(
                        when (phase) {
                            BlindCoachPhase.PROMPTING_GERMAN -> "🎧 Hör zu …"
                            BlindCoachPhase.LISTENING_USER -> "🎤 Jetzt frei aus dem Kopf auf Russisch sprechen!"
                            BlindCoachPhase.PLAYING_MASTER -> "🔊 Hör die Musterlösung & sprich sie laut mit …"
                            BlindCoachPhase.FEEDBACK_PAUSE -> if (lastScore?.passed == true) "🧠 Perfekt abgerufen! Nächster Satz folgt …" else "🔄 Gemerkt — kommt gleich noch einmal dran …"
                        },
                        color = HikariTextMuted,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )

                    if (revealText) {
                        Spacer(Modifier.height(18.dp))
                        RuPhraseBlock(phrase, cyrillicSize = 24.sp)
                    } else {
                        Spacer(Modifier.height(18.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(HikariCardBg)
                                .border(1.dp, HikariBorder, RoundedCornerShape(10.dp))
                                .clickable { revealText = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text("👁️ Ausnahmsweise Text einblenden", color = HikariTextFaint, fontSize = 12.5.sp)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RuGhostButton("🔊 Lösung hören", Modifier.weight(1f)) {
                            recognizer.stopListening()
                            vm.audio.play(phrase.audio)
                        }
                        RuGhostButton("Überspringen →", Modifier.weight(1f)) {
                            recognizer.stopListening()
                            deVoice.stop()
                            if (round + 1 < queue.size) round++ else finished = true
                        }
                    }
                }
            }
        }
    }
}
