package com.hikari.app.ui.russian

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.domain.russian.RuDay
import com.hikari.app.domain.russian.RuLessonBuilder
import com.hikari.app.domain.russian.RuPhrase
import com.hikari.app.domain.russian.RussianCourseRepository
import com.hikari.app.domain.russian.RussianProgressStore
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
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class RussianGamesViewModel @Inject constructor(
    private val repo: RussianCourseRepository,
    private val store: RussianProgressStore,
    private val testSelection: com.hikari.app.domain.russian.RuTestSelection,
    val audio: RussianAudioPlayer,
) : ViewModel() {
    init {
        viewModelScope.launch { audio.warmUp() }
    }

    val index = repo.index
    val progress = store.state
    private val settings = store.state.value.settings

    /**
     * Pool für Hör-Blitz & Paare finden: kombiniert alle bisher im Satzbuch gelernten Sätze
     * (fällige und wackelige Karten zuerst) mit den freigeschalteten Tagen.
     */
    val pool: List<RuPhrase> = run {
        val state = store.state.value
        val today = store.today()
        val dueIds = com.hikari.app.domain.russian.RuSrs.dueIds(state.cards, today)
        val weakIds = state.cards.entries.filter { it.value.box <= 2 }.map { it.key }
        val learnedIds = (dueIds + weakIds + state.cards.keys).distinct()
        val learned = learnedIds.mapNotNull { repo.phrase(it, settings) }
        val unlockedPhrases = index.phrasesUpTo(state.unlockedDay(index.days.size), settings)
        (learned + unlockedPhrases).distinctBy { it.id }
    }

    val phrasebookMeta = repo.phrasebook

    /** Sätze, zu denen es eine ausgearbeitete Vertiefung gibt. */
    val deepIds: Set<String> = repo.deepdive.keys

    /** Legt fest, welche Sätze der Vokabeltest oder Sprech-Reflex abfragt. */
    fun prepareTest(ids: List<String>) { testSelection.ids = ids }

    fun gradeCard(id: String, correct: Boolean, xp: Int = 0, spokenOk: Int = 0) {
        store.gradeSingleCard(id, correct, xp = xp, spokenOk = spokenOk)
    }

    fun removeSentence(ru: String) = store.update { it.copy(mySentences = it.mySentences.filterNot { x -> x.ru == ru }) }

    fun toggleFavorite(id: String) = store.update {
        it.copy(favorites = if (id in it.favorites) it.favorites - id else it.favorites + id)
    }

    /** Alle Sätze der freigeschalteten Tage. */
    fun unlockedDays() = index.days.filter { it.day <= store.state.value.unlockedDay(index.days.size) }

    fun allDays() = index.days + repo.podcastIndex.days

    fun dayPhrases(day: RuDay): List<RuPhrase> =
        if (day.media != null) repo.podcastIndex.dayPhrases(day, settings) else index.dayPhrases(day, settings)

    fun finishBlitz(score: Int): Boolean {
        val best = store.state.value.bestBlitz
        store.update { it.copy(bestBlitz = maxOf(it.bestBlitz, score)) }
        addXp(score / 10)
        return score > best
    }

    fun finishPairs(seconds: Int): Boolean {
        val best = store.state.value.bestPairsSeconds
        store.update { it.copy(bestPairsSeconds = if (best == 0) seconds else minOf(best, seconds)) }
        addXp(15)
        return best == 0 || seconds < best
    }

    private fun addXp(xp: Int) {
        if (xp <= 0) return
        val today = store.today()
        store.update { it.copy(xp = it.xp + xp, xpByDay = it.xpByDay + (today to it.xpOn(today) + xp)) }
    }

    override fun onCleared() {
        audio.stop()
    }
}

// ── Hör-Blitz ────────────────────────────────────────────────────────────────

private const val BLITZ_MS = 60_000L

@Composable
fun RussianBlitzScreen(onClose: () -> Unit, vm: RussianGamesViewModel = hiltViewModel()) {
    val rng = remember { Random(System.nanoTime()) }
    var running by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableLongStateOf(BLITZ_MS) }
    var score by remember { mutableIntStateOf(0) }
    var combo by remember { mutableIntStateOf(0) }
    var round by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    var newBest by remember { mutableStateOf(false) }
    val heard = remember { mutableStateListOf<BlitzHeard>() }
    if (vm.pool.size < 4) {
        GameIntro(title = "Hör-Blitz", text = "Schließ zuerst Tag 1 ab — dann gibt es genug Sätze für das Spiel.", onStart = onClose)
        return
    }
    val question = remember(round) {
        val target = vm.pool.random(rng)
        target to (RuLessonBuilder.distractors(target, vm.pool, rng) + target).shuffled(rng)
    }
    val playing by vm.audio.playing.collectAsState()

    LaunchedEffect(running) {
        while (running && timeLeft > 0) {
            delay(100)
            timeLeft -= 100
        }
        if (running && timeLeft <= 0) {
            running = false
            if (picked == null) heard.record(question.first, null)
            over = true
            vm.audio.stop()
            newBest = vm.finishBlitz(score)
        }
    }
    LaunchedEffect(round, running) { if (running) vm.audio.play(question.first.audio) }
    LaunchedEffect(picked) {
        if (picked != null) {
            delay(if (picked == question.first.id) 450 else 1100)
            picked = null
            round++
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        RuSessionTopBar(
            progress = timeLeft.toFloat() / BLITZ_MS,
            onClose = { vm.audio.stop(); onClose() },
            trailing = "$score",
        )
        when {
            over -> BlitzSummary(
                score = score,
                note = if (newBest) "Neuer Rekord!" else "Rekord: ${vm.progress.value.bestBlitz}",
                heard = heard,
                playingAudio = playing,
                onPlay = { vm.audio.play(it.audio) },
                onAgain = {
                    vm.audio.stop()
                    heard.clear()
                    score = 0; combo = 0; timeLeft = BLITZ_MS; over = false; round++; running = true
                },
                onClose = { vm.audio.stop(); onClose() },
            )
            !running -> GameIntro(
                title = "Hör-Blitz",
                text = "Du hörst einen Satz — tippe so schnell wie möglich die richtige Bedeutung. Serien geben Bonuspunkte, Fehler kosten 3 Sekunden.",
                onStart = { running = true },
            )
            else -> Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(16.dp))
                Text(
                    if (combo >= 3) "Serie ×${multiplier(combo)}" else " ",
                    color = HikariAmber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(16.dp))
                RuPlayRow(
                    playingNow = playing == question.first.audio,
                    onPlay = { vm.audio.play(question.first.audio) },
                    onSlow = { vm.audio.play(question.first.audio, slow = true) },
                    size = 80.dp,
                )
                Spacer(Modifier.height(16.dp))
                if (picked != null) {
                    RuInterlinear(question.first.ru, size = 20.sp)
                } else {
                    Text(" ", fontSize = 18.sp)
                }
                Spacer(Modifier.height(20.dp))
                question.second.forEach { o ->
                    val state = when {
                        picked == null -> RuOptionState.Idle
                        o.id == question.first.id -> RuOptionState.Correct
                        o.id == picked -> RuOptionState.Wrong
                        else -> RuOptionState.Dimmed
                    }
                    RuOptionCard(state, onClick = {
                        if (picked == null) {
                            picked = o.id
                            val isCorrect = o.id == question.first.id
                            heard.record(question.first, isCorrect)
                            vm.gradeCard(question.first.id, correct = isCorrect)
                            if (isCorrect) {
                                combo++
                                score += 10 * multiplier(combo)
                            } else {
                                combo = 0
                                timeLeft = (timeLeft - 3_000).coerceAtLeast(0)
                            }
                        }
                    }) { Text(o.de, color = HikariText, fontSize = 16.sp) }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

private fun multiplier(combo: Int) = (1 + combo / 3).coerceAtMost(4)

/** Ein in dieser Runde gehörter Satz; [correct] = null heißt: Zeit lief ab, bevor getippt wurde. */
internal data class BlitzHeard(val phrase: RuPhrase, val correct: Boolean?)

private fun BlitzHeard.rank() = when (correct) { false -> 0; null -> 1; true -> 2 }

/** Merkt sich jeden Satz einmal — kommt er mehrfach dran, zählt das schlechteste Ergebnis. */
internal fun MutableList<BlitzHeard>.record(phrase: RuPhrase, correct: Boolean?) {
    val entry = BlitzHeard(phrase, correct)
    val i = indexOfFirst { it.phrase.id == phrase.id }
    when {
        i < 0 -> add(entry)
        entry.rank() < this[i].rank() -> this[i] = entry
    }
}

/** Reihenfolge der Abschlussliste: falsch, unbeantwortet, richtig — innerhalb jeweils wie gehört. */
internal fun List<BlitzHeard>.forReview(): List<BlitzHeard> = sortedBy { it.rank() }

@Composable
private fun BlitzSummary(
    score: Int,
    note: String,
    heard: List<BlitzHeard>,
    playingAudio: String?,
    onPlay: (RuPhrase) -> Unit,
    onAgain: () -> Unit,
    onClose: () -> Unit,
) {
    val review = remember(heard.toList()) { heard.forReview() }
    val right = heard.count { it.correct == true }
    val wrong = heard.count { it.correct == false }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                RuOverline("Zeit um!", color = HikariAmber)
                Spacer(Modifier.height(10.dp))
                Text("$score Punkte", color = HikariText, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(note, color = HikariTextMuted, fontSize = 15.sp)
                if (heard.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("$right richtig · $wrong falsch", color = HikariTextFaint, fontSize = 13.sp)
                }
                Spacer(Modifier.height(24.dp))
                RuPrimaryButton("Nochmal", onClick = onAgain)
                Spacer(Modifier.height(10.dp))
                RuGhostButton("Fertig", Modifier.fillMaxWidth(), onClose)
                Spacer(Modifier.height(20.dp))
            }
        }
        if (review.isNotEmpty()) {
            item { RuOverline("Gehörte Sätze · ${review.size}", Modifier.padding(start = 4.dp, bottom = 2.dp)) }
            items(review, key = { it.phrase.id }) { h ->
                BlitzReviewRow(h, playing = playingAudio == h.phrase.audio, onPlay = { onPlay(h.phrase) })
            }
        }
    }
}

@Composable
private fun BlitzReviewRow(h: BlitzHeard, playing: Boolean, onPlay: () -> Unit) {
    val dot = when (h.correct) {
        true -> RuGood
        false -> RuBad
        null -> HikariTextFaint
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(HikariCardBg)
            .clickable(onClick = onPlay).padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            RuCompactPhrase(h.phrase.ru, size = 17.sp)
            Spacer(Modifier.height(4.dp))
            Text(h.phrase.de, color = HikariTextMuted, fontSize = 14.sp, lineHeight = 19.sp)
        }
        Icon(
            Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = "Anhören",
            tint = if (playing) HikariAmber else HikariTextMuted,
            modifier = Modifier.padding(8.dp).size(22.dp),
        )
    }
}

@Composable
private fun GameIntro(title: String, text: String, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = HikariText, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(text, color = HikariTextMuted, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
        Spacer(Modifier.height(30.dp))
        RuPrimaryButton("Start", onClick = onStart)
    }
}

@Composable
private fun GameOver(title: String, value: String, note: String, onAgain: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RuOverline(title, color = HikariAmber)
        Spacer(Modifier.height(10.dp))
        Text(value, color = HikariText, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(note, color = HikariTextMuted, fontSize = 15.sp)
        Spacer(Modifier.height(32.dp))
        RuPrimaryButton("Nochmal", onClick = onAgain)
        Spacer(Modifier.height(10.dp))
        RuGhostButton("Fertig", Modifier.fillMaxWidth(), onClose)
    }
}

// ── Paare finden ─────────────────────────────────────────────────────────────

private data class PairCard(val phrase: RuPhrase, val russian: Boolean)

@Composable
fun RussianPairsScreen(onClose: () -> Unit, vm: RussianGamesViewModel = hiltViewModel()) {
    var game by remember { mutableIntStateOf(0) }
    val cards = remember(game) {
        val rng = Random(System.nanoTime())
        val chosen = mutableListOf<RuPhrase>()
        for (p in vm.pool.shuffled(rng)) {
            if (chosen.size == 6) break
            if (chosen.any { RuLessonBuilder.confusable(it.de, p.de) || it.plain == p.plain }) continue
            chosen += p
        }
        chosen.flatMap { listOf(PairCard(it, true), PairCard(it, false)) }.shuffled(rng)
    }
    val matched = remember(game) { mutableStateListOf<String>() }
    var selected by remember(game) { mutableStateOf<Int?>(null) }
    var wrong by remember(game) { mutableStateOf<Pair<Int, Int>?>(null) }
    var started by remember(game) { mutableStateOf(false) }
    var elapsed by remember(game) { mutableLongStateOf(0L) }
    var penalty by remember(game) { mutableIntStateOf(0) }
    var newBest by remember(game) { mutableStateOf(false) }
    val done = cards.isNotEmpty() && matched.size * 2 == cards.size
    val seconds = (elapsed / 1000).toInt() + penalty

    LaunchedEffect(started, done) {
        while (started && !done) {
            delay(100)
            elapsed += 100
        }
        // Live-States lesen: `seconds` wäre hier der Wert vom Spielstart (0).
        if (started && done) newBest = vm.finishPairs((elapsed / 1000).toInt() + penalty)
    }
    LaunchedEffect(wrong) {
        if (wrong != null) {
            delay(650)
            wrong = null
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        RuSessionTopBar(
            progress = if (cards.isEmpty()) 0f else matched.size * 2f / cards.size,
            onClose = { vm.audio.stop(); onClose() },
            trailing = "$seconds s",
        )
        when {
            done -> GameOver(
                title = "Alle Paare!",
                value = "$seconds Sekunden",
                note = if (newBest) "Neue Bestzeit!" else "Bestzeit: ${vm.progress.value.bestPairsSeconds} s",
                onAgain = { game++ },
                onClose = onClose,
            )
            !started -> GameIntro(
                title = "Paare finden",
                text = "Verbinde jeden russischen Satz mit seiner Bedeutung. Russische Karten sprechen beim Antippen — Fehler kosten 3 Sekunden.",
                onStart = { started = true },
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(cards) { i, c ->
                    val isMatched = c.phrase.id in matched
                    val isWrong = wrong?.let { it.first == i || it.second == i } == true
                    val fade by animateFloatAsState(if (isMatched) 0.18f else 1f, tween(300), label = "pair")
                    Box(
                        Modifier
                            .heightIn(min = 92.dp)
                            .alpha(fade)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected == i) HikariSurfaceHigh else HikariCardBg)
                            .border(
                                1.5.dp,
                                when {
                                    isWrong -> RuBad
                                    selected == i -> HikariAmber
                                    else -> HikariBorder
                                },
                                RoundedCornerShape(14.dp),
                            )
                            .clickable(enabled = !isMatched && wrong == null) {
                                if (c.russian) vm.audio.play(c.phrase.audio)
                                val s = selected
                                when {
                                    s == null -> selected = i
                                    s == i -> selected = null
                                    cards[s].russian == c.russian -> selected = i
                                    cards[s].phrase.id == c.phrase.id -> {
                                        matched += c.phrase.id
                                        vm.gradeCard(c.phrase.id, correct = true)
                                        selected = null
                                    }
                                    else -> {
                                        wrong = s to i
                                        vm.gradeCard(cards[s].phrase.id, correct = false)
                                        penalty += 3
                                        selected = null
                                    }
                                }
                            }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (c.russian) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Memory: keine Bedeutungen — die sind ja das Rätsel.
                                RuInterlinear(c.phrase.ru, size = 16.sp, showGloss = false)
                            }
                        } else {
                            Text(c.phrase.de, color = HikariText.copy(alpha = 0.85f), fontSize = 14.5.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
