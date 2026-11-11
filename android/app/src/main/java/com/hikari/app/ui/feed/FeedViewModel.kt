package com.hikari.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.TodayCountResponse
import com.hikari.app.data.prefs.FeedPreferences
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.feed.BuildGame
import com.hikari.app.domain.feed.ChoiceGame
import com.hikari.app.domain.feed.DigitSpanGame
import com.hikari.app.domain.feed.FeedSkips
import com.hikari.app.domain.feed.SequenceGame
import com.hikari.app.domain.feed.FeedComposer
import com.hikari.app.domain.feed.FeedContentRepository
import com.hikari.app.domain.feed.FeedEntry
import com.hikari.app.domain.feed.FeedMix
import com.hikari.app.domain.feed.FeedFocus
import com.hikari.app.domain.feed.FeedGame
import com.hikari.app.domain.feed.FeedGameFactory
import com.hikari.app.domain.feed.LanguageCardItem
import com.hikari.app.domain.feed.LearningLanguage
import com.hikari.app.domain.feed.LetterIntroGame
import com.hikari.app.domain.feed.MemoryGridGame
import com.hikari.app.domain.feed.MindfulCard
import com.hikari.app.domain.feed.PairsGame
import com.hikari.app.domain.feed.RuAlphabet
import com.hikari.app.domain.feed.RuAlphabetLogic
import com.hikari.app.domain.feed.RuFeedGrading
import com.hikari.app.domain.feed.RuGrade
import com.hikari.app.domain.feed.RussianFeedContext
import com.hikari.app.domain.feed.MindfulFeedContentProvider
import com.hikari.app.domain.feed.MindfulModuleType
import com.hikari.app.domain.feed.ModuleRank
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.domain.repo.FeedRepository
import com.hikari.app.domain.russian.RuLevel
import com.hikari.app.domain.russian.RuProgress
import com.hikari.app.domain.russian.RussianStudyTime
import com.hikari.app.domain.russian.RuSessionResult
import com.hikari.app.domain.russian.RussianCourseRepository
import com.hikari.app.domain.russian.RussianProgressStore
import com.hikari.app.domain.russian.apply
import com.hikari.app.ui.russian.RussianAudioPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

enum class FeedMode { NEW, SAVED, OLD }

/** Was ein beendetes Spiel bewirkt hat — die Rückmeldung, die der Feed danach zeigt. */
data class GameOutcome(val correct: Boolean, val message: String = "", val xp: Int = 0)

/** So viele Seiten vor dem Ende lädt der Feed den nächsten Schwung. */
private const val LOAD_AHEAD = 5
private const val PAIRS_XP = 6
private const val SPEECH_XP = 15

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repo: FeedRepository,
    private val settings: SettingsStore,
    private val feedPrefs: FeedPreferences? = null,
    val audio: RussianAudioPlayer? = null,
    val russianRepo: RussianCourseRepository? = null,
    val russianProgress: RussianProgressStore? = null,
    private val content: FeedContentRepository? = null,
    private val studyTime: RussianStudyTime? = null,
) : ViewModel() {

    constructor(repo: FeedRepository, settings: SettingsStore) : this(repo, settings, null, null, null, null, null, null)

    val backendUrl: StateFlow<String> = settings.backendUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // ── Mindful Feed 2.0 (Geistige Nahrung & Kognitive Bereicherung) ─────────────

    val enabledModules: StateFlow<Set<MindfulModuleType>> =
        feedPrefs?.enabledModules ?: MutableStateFlow(MindfulModuleType.entries.toSet())

    val selectedLanguage: StateFlow<LearningLanguage> =
        feedPrefs?.selectedLanguage ?: MutableStateFlow(LearningLanguage.RUSSIAN)

    val moduleRanks: StateFlow<Map<MindfulModuleType, ModuleRank>> =
        feedPrefs?.moduleRanks ?: MutableStateFlow(
            MindfulModuleType.entries.associateWith {
                when (it) {
                    MindfulModuleType.LANGUAGE -> ModuleRank.RANK_3
                    MindfulModuleType.BRAIN_PUZZLE, MindfulModuleType.QUOTE -> ModuleRank.RANK_2
                    else -> ModuleRank.RANK_1
                }
            }
        )

    val completedCards: StateFlow<Set<String>> =
        feedPrefs?.completedCards ?: MutableStateFlow(emptySet())

    val savedCardIds: StateFlow<Set<String>> =
        feedPrefs?.savedCardIds ?: MutableStateFlow(emptySet())

    val commuterMode: StateFlow<Boolean> =
        feedPrefs?.commuterMode ?: MutableStateFlow(false)

    /** Russisch-Fokus: der Feed zeigt nur noch den Russisch-Kurs; die Mischung bleibt gespeichert. */
    val russianFocus: StateFlow<Boolean> =
        feedPrefs?.russianFocus ?: MutableStateFlow(false)

    fun setRussianFocus(enabled: Boolean) {
        feedPrefs?.setRussianFocus(enabled)
    }

    /** Was gerade gilt: die eingestellte Mischung, oder im Fokus nur Russisch. */
    private fun mix(): FeedMix = FeedFocus.mix(
        russianFocus.value,
        FeedMix(enabledModules.value, moduleRanks.value, selectedLanguage.value),
    )

    private val cardCache = mutableMapOf<String, MindfulCard>()

    // ── Endloser Feed ───────────────────────────────────────────────────────────
    // Der Feed ist keine feste Tagesliste mehr: Er wird beim Öffnen frisch gemischt und
    // wächst beim Scrollen nach. Wissenskarten wandern durch den ganzen Vorrat, bevor sich
    // etwas wiederholt; Spiele und Russisch-Übungen werden jedes Mal neu erzeugt.

    private val legacyCards: Map<MindfulModuleType, List<MindfulCard>> by lazy {
        MindfulFeedContentProvider.getAllMasterCards().filter { it !is LanguageCardItem }.groupBy { it.type }
    }

    private fun cardsOf(module: MindfulModuleType): List<MindfulCard> =
        content?.cards(module) ?: legacyCards[module].orEmpty()

    private var composer = FeedComposer(::cardsOf)
    private var currentIndex = 0
    private var buildJob: Job? = null

    private val _entries = MutableStateFlow<List<FeedEntry>>(emptyList())
    val entries: StateFlow<List<FeedEntry>> = _entries.asStateFlow()

    /** Nur die Wissenskarten des Feeds (für »Mein Gehirn« und ältere Aufrufer). */
    val mindfulCards: StateFlow<List<MindfulCard>> = _entries
        .map { list -> list.filterIsInstance<FeedEntry.Card>().map { it.card } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dailyGoal: Int = FeedComposer.DAILY_GOAL

    val completedCount: StateFlow<Int> = completedCards
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val letterProgress: StateFlow<Map<String, Int>> =
        feedPrefs?.letterProgress ?: MutableStateFlow(emptyMap())

    val alphabet: RuAlphabet get() = content?.alphabet ?: RuAlphabet()

    init {
        viewModelScope.launch {
            combine(enabledModules, selectedLanguage, moduleRanks, russianFocus) { _, _, _, _ -> mix() }
                .distinctUntilChanged()
                .collect { rebuild() }
        }
    }

    /** Neue Sitzung: frisch mischen und von vorn beginnen. */
    private fun rebuild() {
        buildJob?.cancel()
        buildJob = viewModelScope.launch {
            // Beim ersten Mal die Inhaltsdateien abseits des Hauptthreads einlesen.
            if (content != null) withContext(Dispatchers.Default) { mix().enabled.forEach { cardsOf(it) }; content.alphabet; content.warmGlossary() }
            composer = FeedComposer(::cardsOf)
            currentIndex = 0
            _entries.value = composeBatch()
        }
    }

    private fun russianContext(): RussianFeedContext? {
        val repo = russianRepo ?: return null
        val store = russianProgress ?: return null
        return runCatching {
            val progress = store.state.value
            RussianFeedContext(
                index = repo.index,
                progress = progress,
                today = store.today(),
                alphabet = alphabet,
                letterProgress = letterProgress.value,
                deepdive = repo.deepdive,
                level = RuLevel.estimate(RuLevel.measure(progress, studyTime?.talkCount() ?: 0)),
                resolve = { id -> repo.phrase(id, progress.settings) },
            )
        }.getOrNull()
    }

    private fun composeBatch(): List<FeedEntry> {
        val mix = mix()
        val language = mix.language
        return composer.batch(
            enabled = mix.enabled,
            ranks = mix.ranks,
            language = language,
            lastSeen = feedPrefs?.lastSeen().orEmpty(),
            russian = russianContext(),
            memoryLevel = feedPrefs?.memoryLevel() ?: 4,
            legacyLanguageCards = MindfulFeedContentProvider.languagePool(language),
            skipped = feedPrefs?.skipped().orEmpty(),
            round = feedPrefs?.nextRound() ?: 0,
            levels = mapOf(
                "digits" to (feedPrefs?.level("digits", FeedGameFactory.DIGITS_START) ?: FeedGameFactory.DIGITS_START),
                "sequence" to (feedPrefs?.level("sequence", FeedGameFactory.SEQUENCE_START) ?: FeedGameFactory.SEQUENCE_START),
            ),
        ).also { batch ->
            batch.forEach { if (it is FeedEntry.Card) cardCache[it.card.id] = it.card }
        }
    }

    private var shownId: String? = null
    private var shownAt = 0L

    /**
     * Die Seite [index] steht jetzt im Bild. Die vorige Karte wird jetzt erst bewertet: gelesen
     * (lange genug da, erledigt oder gemerkt) oder nur überwischt. Außerdem rechtzeitig nachladen.
     */
    fun onShown(index: Int) {
        val list = _entries.value
        val entry = list.getOrNull(index) ?: return
        currentIndex = index
        if (entry.id != shownId) {
            settlePrevious()
            shownId = entry.id
            shownAt = System.currentTimeMillis()
        }
        if (index >= list.size - LOAD_AHEAD) {
            val more = composeBatch()
            if (more.isNotEmpty()) _entries.update { it + more }
        }
    }

    /** Bewertet die Karte, die gerade verlassen wird — nur Wissenskarten haben einen Gesehen-Verlauf. */
    private fun settlePrevious() {
        val id = shownId ?: return
        val prefs = feedPrefs ?: return
        if (_entries.value.none { it.id == id && it is FeedEntry.Card }) return
        val read = System.currentTimeMillis() - shownAt >= FeedSkips.SEEN_AFTER_MS ||
            id in completedCards.value || id in savedCardIds.value
        if (read) {
            prefs.clearSkipped(id)
            prefs.markSeen(id)
        } else if (prefs.markSkipped(id)) {
            // Zweimal überwischt: gilt als gesehen und reiht sich hinten in die Rotation ein.
            prefs.markSeen(id)
        }
    }

    override fun onCleared() {
        settlePrevious()
        super.onCleared()
    }

    /** Unter welchem Modus die Zeit auf diesem Eintrag in der Russisch-Statistik zählt (null = gar nicht). */
    private fun studyMode(entry: FeedEntry): String? = when {
        entry is FeedEntry.Card && entry.card is LanguageCardItem ->
            if ((entry.card as LanguageCardItem).language == LearningLanguage.RUSSIAN) RussianStudyTime.FEED_PHRASES else null
        entry is FeedEntry.Game && entry.game.module == MindfulModuleType.LANGUAGE ->
            if (entry.game.label.startsWith("KYRILLISCH")) RussianStudyTime.FEED_ALPHABET else RussianStudyTime.FEED_PRACTICE
        else -> null
    }

    /**
     * [seconds] aktive Zeit auf der Seite [index]. Zählt immer für die Feed-Zeit des Tages;
     * Russisch-Einträge zählen zusätzlich als Lernzeit im Russisch-Bereich (Bereich »Feed«).
     */
    fun addActiveTime(index: Int, seconds: Int) {
        val entry = _entries.value.getOrNull(index) ?: return
        feedPrefs?.addFeedSeconds(seconds)
        studyMode(entry)?.let { studyTime?.add(it, seconds) }
    }

    fun feedSecondsToday(): Int = feedPrefs?.feedSecondsToday() ?: 0

    val savedCards: StateFlow<List<MindfulCard>> = combine(
        savedCardIds,
        mindfulCards,
    ) { savedIds, currentCards ->
        currentCards.forEach { cardCache[it.id] = it }
        savedIds.mapNotNull { id ->
            cardCache[id] ?: content?.find(id) ?: russianCard(id) ?: MindfulFeedContentProvider.findCardById(id)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Gemerkte Russisch-Karte (ID »ru:art:satz«) aus dem Kurs wieder aufbauen — sie steht in keiner Datei. */
    private fun russianCard(id: String): MindfulCard? {
        if (!id.startsWith("ru:")) return null
        val ctx = russianContext() ?: return null
        return runCatching { composer.languageCardFor(ctx, id.split(':', limit = 3).getOrNull(2).orEmpty()) }.getOrNull()
            ?.copy(id = id)
            ?.also { cardCache[id] = it }
    }

    /** Anteil des Tagesziels, der geschafft ist. */
    val progressFraction: StateFlow<Float> = completedCount
        .map { (it.toFloat() / dailyGoal).coerceIn(0f, 1f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    val isGoalCompleted: StateFlow<Boolean> = completedCount
        .map { it >= dailyGoal }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun markCardCompleted(cardId: String) {
        if (cardId in completedCards.value) return
        feedPrefs?.markCardCompleted(cardId)
        if (completedCards.value.size >= dailyGoal) {
            feedPrefs?.incrementStreakIfEligible()
            celebrateGoal()
        }
    }

    /** Tagesziel geschafft: einmal am Tag kommt direkt nach der aktuellen Karte die Abschluss-Seite. */
    private fun celebrateGoal() {
        val prefs = feedPrefs ?: return
        if (prefs.goalCelebratedToday()) return
        prefs.markGoalCelebrated()
        _entries.update { list ->
            val at = (currentIndex + 1).coerceAtMost(list.size)
            list.toMutableList().apply { add(at, FeedEntry.Milestone("milestone:${System.currentTimeMillis()}")) }
        }
    }

    fun toggleSavedCard(cardId: String): Boolean {
        return feedPrefs?.toggleSavedCard(cardId) ?: false
    }

    fun toggleCommuterMode(): Boolean {
        return feedPrefs?.toggleCommuterMode() ?: false
    }

    fun setCommuterMode(enabled: Boolean) {
        feedPrefs?.setCommuterMode(enabled)
    }


    fun toggleModule(module: MindfulModuleType, enabled: Boolean) {
        feedPrefs?.setModuleEnabled(module, enabled)
    }

    fun setModuleRank(module: MindfulModuleType, rank: ModuleRank) {
        feedPrefs?.setModuleRank(module, rank)
    }

    fun setLearningLanguage(language: LearningLanguage) {
        feedPrefs?.setSelectedLanguage(language)
    }

    fun resetDailyProgress() {
        feedPrefs?.resetDailyProgress()
    }

    /** Setzt den Alphabet-Lernstand zurück — der Kursfortschritt selbst gehört dem Russisch-Bereich. */
    fun resetLanguageProgress() {
        feedPrefs?.setLetterProgress(emptyMap())
        rebuild()
    }

    fun getStreak(): Int = feedPrefs?.getStreak() ?: 1

    // ── Rückmeldung zu Spielen und Übungen ──────────────────────────────────────

    /**
     * Ein Spiel ist beendet. Trägt das Ergebnis dort ein, wo es hingehört (Wiederholungsplan,
     * Alphabet, Schwierigkeit) und sagt in einem Satz, was das bewirkt hat.
     */
    fun finishGame(game: FeedGame, correct: Boolean): GameOutcome {
        markCardCompleted(game.id)
        return when (game) {
            is ChoiceGame -> {
                game.letter?.let { gradeLetter(it, correct) }
                game.grade?.let { gradeRussian(it, correct) } ?: GameOutcome(correct)
            }
            is BuildGame -> game.grade?.let { gradeRussian(it, correct) } ?: GameOutcome(correct)
            is LetterIntroGame -> {
                introduceLetter(game.letter.l)
                GameOutcome(correct, "${game.letter.u} gehört jetzt zu deinen Buchstaben. Er kommt in den nächsten Übungen wieder.")
            }
            is PairsGame -> {
                if (!game.letters && correct) awardXp(PAIRS_XP)
                GameOutcome(correct, xp = if (!game.letters && correct) PAIRS_XP else 0)
            }
            is DigitSpanGame -> levelOutcome(
                "digits", game.level, correct, FeedGameFactory.DIGITS_MIN, FeedGameFactory.DIGITS_MAX, "Ziffern",
            )
            is SequenceGame -> levelOutcome(
                "sequence", game.level, correct, FeedGameFactory.SEQUENCE_MIN, FeedGameFactory.SEQUENCE_MAX, "Felder",
            )
            is MemoryGridGame -> {
                val next = (game.level + if (correct) 1 else -1)
                    .coerceIn(FeedGameFactory.MEMORY_MIN_LEVEL, FeedGameFactory.MEMORY_MAX_LEVEL)
                feedPrefs?.setMemoryLevel(next)
                GameOutcome(
                    correct,
                    when {
                        next > game.level -> "Nächstes Mal sind es $next Felder."
                        next < game.level -> "Nächstes Mal sind es wieder $next Felder."
                        correct -> "Mehr als ${game.level} Felder zeigt das Spiel nicht. Stark."
                        else -> "Es bleibt bei ${game.level} Feldern."
                    },
                )
            }
            else -> GameOutcome(correct)
        }
    }

    /** Spiele mit Stufe: Erfolg macht es eins schwerer, ein Fehler eins leichter. */
    private fun levelOutcome(key: String, level: Int, correct: Boolean, min: Int, max: Int, unit: String): GameOutcome {
        val next = (level + if (correct) 1 else -1).coerceIn(min, max)
        feedPrefs?.setLevel(key, next)
        return GameOutcome(
            correct,
            when {
                next > level -> "Nächstes Mal sind es $next $unit."
                next < level -> "Nächstes Mal sind es wieder $next $unit."
                correct -> "Mehr als $level $unit gibt es nicht. Stark."
                else -> "Es bleibt bei $level $unit."
            },
        )
    }

    private fun gradeRussian(grade: RuGrade, correct: Boolean): GameOutcome {
        val store = russianProgress ?: return GameOutcome(correct)
        val today = store.today()
        val card = runCatching { store.state.value.cards[grade.phraseId] }.getOrNull()
        val xp = RuFeedGrading.xp(grade.due, correct)
        val message = RuFeedGrading.message(card, grade.due, correct, today)
        if (RuFeedGrading.counts(card, grade.due, correct)) {
            store.gradeSingleCard(grade.phraseId, correct, xp)
        } else if (xp > 0) {
            awardXp(xp)
        }
        return GameOutcome(correct, message, xp)
    }

    private fun awardXp(xp: Int) {
        val store = russianProgress ?: return
        val today = store.today()
        store.update { it.apply(RuSessionResult(xp = xp, graded = emptyMap()), today) }
    }

    private fun gradeLetter(letter: String, correct: Boolean) {
        val prefs = feedPrefs ?: return
        prefs.setLetterProgress(RuAlphabetLogic.graded(prefs.letterProgress.value, letter, correct))
    }

    private fun introduceLetter(letter: String) {
        val prefs = feedPrefs ?: return
        val now = prefs.letterProgress.value
        if ((now[letter] ?: 0) < 1) prefs.setLetterProgress(now + (letter to 1))
    }

    /**
     * Nachsprechen auf der Satz-Karte. Punkte gibt es nur für eine bestandene Aussprache-Prüfung;
     * in den Wiederholungsplan zählt sie nur bei Sätzen, die im Kurs schon dran waren.
     */
    fun recordRussianPractice(phraseId: String?, spokenCorrect: Boolean) {
        val store = russianProgress ?: return
        val today = store.today()
        val known = phraseId != null && runCatching { store.state.value.cards.containsKey(phraseId) }.getOrDefault(false)
        store.update { current ->
            current.apply(
                RuSessionResult(
                    xp = if (spokenCorrect) SPEECH_XP else 0,
                    graded = if (known && spokenCorrect) mapOf(phraseId!! to true) else emptyMap(),
                    spokenOk = if (spokenCorrect) 1 else 0,
                ),
                today = today,
            )
        }
    }

    // ── Legacy Compatibility (für FeedRepository / Tests) ───────────────────────

    private val _mode = MutableStateFlow(FeedMode.NEW)
    val mode: StateFlow<FeedMode> = _mode.asStateFlow()

    private val _savedItems = MutableStateFlow<List<FeedItem>>(emptyList())
    private val _oldItems = MutableStateFlow<List<FeedItem>>(emptyList())
    private val _saveOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setMode(newMode: FeedMode) {
        _mode.value = newMode
        when (newMode) {
            FeedMode.NEW -> refresh()
            FeedMode.SAVED -> loadSaved()
            FeedMode.OLD -> loadOld()
        }
    }

    private fun loadOld() = viewModelScope.launch {
        _refreshing.value = true
        runCatching { repo.fetchOld() }
            .onSuccess {
                _oldItems.value = it.distinctBy { item -> item.videoId }
                _error.value = null
            }
            .onFailure { _error.value = it.message ?: "Archiv konnte nicht geladen werden" }
        _refreshing.value = false
    }

    private fun loadSaved() = viewModelScope.launch {
        _refreshing.value = true
        runCatching { repo.fetchSaved() }
            .onSuccess {
                _savedItems.value = it.distinctBy { item -> item.videoId }
                _error.value = null
            }
            .onFailure { _error.value = it.message ?: "Gespeicherte Videos konnten nicht geladen werden" }
        _refreshing.value = false
    }

    private val newItems: StateFlow<List<FeedItem>> =
        repo.newItems()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val items: StateFlow<List<FeedItem>> =
        combine(_mode, newItems, _savedItems, _oldItems, _saveOverrides) { mode, newL, savedL, oldL, overrides ->
            val base = when (mode) {
                FeedMode.NEW -> newL
                FeedMode.SAVED -> savedL
                FeedMode.OLD -> oldL
            }
            val patched = base
                .distinctBy { it.videoId }
                .withSaveOverrides(overrides)
            if (mode == FeedMode.SAVED) patched.filter { it.saved } else patched
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _today = MutableStateFlow<TodayCountResponse?>(null)
    val today: StateFlow<TodayCountResponse?> = _today.asStateFlow()

    init {
        refresh(pull = true)
    }

    fun refresh(pull: Boolean = false) = viewModelScope.launch {
        exhausted = false
        _refreshing.value = true
        _error.value = null
        if (pull) {
            runCatching { repo.refresh() }
                .onFailure { _error.value = it.message ?: "Aktualisierung fehlgeschlagen" }
        }
        _refreshing.value = false
    }

    private var exhausted = false
    private var fetchingMore = false

    fun loadMore() {
        if (_mode.value != FeedMode.NEW || fetchingMore || exhausted) return
        fetchingMore = true
        viewModelScope.launch {
            try {
                val added = runCatching { repo.loadMore(items.value.size) }.getOrDefault(0)
                if (added == 0) exhausted = true
            } finally {
                fetchingMore = false
            }
        }
    }

    fun onSeen(id: String) = viewModelScope.launch {
        if (_mode.value == FeedMode.NEW) repo.markSeen(id)
    }

    fun onToggleSave(id: String, currentlySaved: Boolean) = viewModelScope.launch {
        val newSaved = !currentlySaved
        _saveOverrides.update { it + (id to newSaved) }
        yield()

        runCatching { repo.toggleSave(id, currentlySaved) }
            .onSuccess {
                _savedItems.value = _savedItems.value
                    .map { if (it.videoId == id) it.copy(saved = newSaved) else it }
                    .filter { it.saved }
                _oldItems.value = _oldItems.value.map {
                    if (it.videoId == id) it.copy(saved = newSaved) else it
                }
                _saveOverrides.update { it - id }
                _error.value = null
            }
            .onFailure {
                _saveOverrides.update { it - id }
                _error.value = it.message ?: "Speicherstatus konnte nicht aktualisiert werden"
            }
    }

    fun toggleSave(videoId: String, currentSaved: Boolean) = onToggleSave(videoId, currentSaved)
    fun markWatched(videoId: String) = onSeen(videoId)

    fun resetSaveOverride(videoId: String) {
        _saveOverrides.update { it - videoId }
    }

    private fun List<FeedItem>.withSaveOverrides(overrides: Map<String, Boolean>): List<FeedItem> {
        if (overrides.isEmpty()) return this
        return map { item ->
            val override = overrides[item.videoId] ?: return@map item
            item.copy(saved = override)
        }
    }
}
