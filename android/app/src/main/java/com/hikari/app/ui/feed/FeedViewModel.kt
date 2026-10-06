package com.hikari.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.TodayCountResponse
import com.hikari.app.data.prefs.FeedPreferences
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.feed.LearningLanguage
import com.hikari.app.domain.feed.MindfulCard
import com.hikari.app.domain.feed.MindfulFeedContentProvider
import com.hikari.app.domain.feed.MindfulModuleType
import com.hikari.app.domain.feed.ModuleRank
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.domain.repo.FeedRepository
import com.hikari.app.domain.russian.RuProgress
import com.hikari.app.domain.russian.RuSessionResult
import com.hikari.app.domain.russian.RussianCourseRepository
import com.hikari.app.domain.russian.RussianProgressStore
import com.hikari.app.domain.russian.apply
import com.hikari.app.ui.russian.RussianAudioPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

enum class FeedMode { NEW, SAVED, OLD }

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repo: FeedRepository,
    private val settings: SettingsStore,
    private val feedPrefs: FeedPreferences? = null,
    val audio: RussianAudioPlayer? = null,
    val russianRepo: RussianCourseRepository? = null,
    val russianProgress: RussianProgressStore? = null,
) : ViewModel() {

    constructor(repo: FeedRepository, settings: SettingsStore) : this(repo, settings, null, null, null, null)

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

    val mindfulCards: StateFlow<List<MindfulCard>> = combine(
        enabledModules,
        selectedLanguage,
        moduleRanks,
        russianProgress?.state ?: MutableStateFlow(RuProgress()),
    ) { modules, lang, ranks, _ ->
        MindfulFeedContentProvider.getDailyCards(
            calendar = Calendar.getInstance(),
            enabledModules = modules,
            language = lang,
            moduleRanks = ranks,
            russianRepo = russianRepo,
            russianProgress = russianProgress,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val progressFraction: StateFlow<Float> = combine(mindfulCards, completedCards) { cards, completed ->
        if (cards.isEmpty()) 1f else (cards.count { it.id in completed }.toFloat() / cards.size.toFloat()).coerceIn(0f, 1f)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    val isGoalCompleted: StateFlow<Boolean> = combine(mindfulCards, completedCards) { cards, completed ->
        cards.isNotEmpty() && cards.all { it.id in completed }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun markCardCompleted(cardId: String) {
        feedPrefs?.markCardCompleted(cardId)
        val allNowDone = mindfulCards.value.isNotEmpty() && mindfulCards.value.all {
            it.id == cardId || it.id in (completedCards.value)
        }
        if (allNowDone) {
            feedPrefs?.incrementStreakIfEligible()
        }
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

    fun resetLanguageProgress() {
        feedPrefs?.resetLanguageProgress()
    }

    fun getStreak(): Int = feedPrefs?.getStreak() ?: 1

    fun recordRussianPractice(phraseId: String?, spokenCorrect: Boolean) {
        val store = russianProgress ?: return
        val today = store.today()
        store.update { current ->
            val graded = if (phraseId != null) mapOf(phraseId to spokenCorrect) else emptyMap()
            current.apply(
                RuSessionResult(
                    xp = if (spokenCorrect) 15 else 5,
                    graded = graded,
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
