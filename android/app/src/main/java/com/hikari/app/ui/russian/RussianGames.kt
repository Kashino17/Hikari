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
    repo: RussianCourseRepository,
    private val store: RussianProgressStore,
    val audio: RussianAudioPlayer,
) : ViewModel() {
    init {
        viewModelScope.launch { audio.warmUp() }
    }

    val index = repo.index
    val progress = store.state
    private val settings = store.state.value.settings

    /** Gelernte Karten; zu Beginn (noch nichts gelernt) die Sätze von Tag 1. */
    val pool: List<RuPhrase> = run {
        val learned = store.state.value.cards.keys.mapNotNull { index.phrase(it, settings) }
        if (learned.size >= 8) learned else index.phrasesUpTo(store.state.value.unlockedDay(index.days.size), settings)
    }

    /** Alle Sätze der freigeschalteten Tage — für das Satzbuch. */
    fun unlockedDays() = index.days.filter { it.day <= store.state.value.unlockedDay(index.days.size) }

    fun dayPhrases(day: Int) = index.day(day)?.let { index.dayPhrases(it, settings) }.orEmpty()

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
            over -> GameOver(
                title = "Zeit um!",
                value = "$score Punkte",
                note = if (newBest) "Neuer Rekord!" else "Rekord: ${vm.progress.value.bestBlitz}",
                onAgain = {
                    score = 0; combo = 0; timeLeft = BLITZ_MS; over = false; round++; running = true
                },
                onClose = onClose,
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
                Text(
                    if (picked != null) question.first.display else " ",
                    color = HikariTextMuted,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )
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
                            if (o.id == question.first.id) {
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
                                        selected = null
                                    }
                                    else -> {
                                        wrong = s to i
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
                                Text(c.phrase.display, color = HikariText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                Text(translitAnnotated(c.phrase.ru), color = HikariTextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
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

// ── Satzbuch ─────────────────────────────────────────────────────────────────

@Composable
fun RussianPhrasebookScreen(onBack: () -> Unit, vm: RussianGamesViewModel = hiltViewModel()) {
    val playing by vm.audio.playing.collectAsState()
    val progress by vm.progress.collectAsState()
    val days = remember { vm.unlockedDays() }
    LazyColumn(
        Modifier.fillMaxSize().background(HikariBg),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText)
                }
                Column(Modifier.padding(start = 4.dp)) {
                    Text("Satzbuch", color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Tippen zum Anhören · Balken zeigen, wie gut es sitzt", color = HikariTextMuted, fontSize = 12.5.sp)
                }
            }
        }
        days.forEach { d ->
            item(key = "h${d.day}") {
                RuOverline("Tag ${d.day} · ${d.title}", Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp))
            }
            items(vm.dayPhrases(d.day), key = { it.id }) { p ->
                val box = progress.cards[p.id]?.box ?: 0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.audio.play(p.audio) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.display, color = HikariText, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(translitAnnotated(p.ru), color = HikariTextMuted, fontSize = 13.sp)
                        Text(p.de, color = HikariTextFaint, fontSize = 12.5.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Icon(
                            Icons.AutoMirrored.Outlined.VolumeUp,
                            null,
                            tint = if (playing == p.audio) HikariAmber else HikariTextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(6) { i ->
                                Box(
                                    Modifier
                                        .size(width = 6.dp, height = 3.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(if (i < box) HikariAmber else HikariBorderStrong),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
