package com.hikari.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.hikari.app.domain.feed.FeedRotation
import com.hikari.app.domain.feed.FeedSkips
import com.hikari.app.domain.feed.LearningLanguage
import com.hikari.app.domain.feed.MindfulModuleType
import com.hikari.app.domain.feed.ModuleRank
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class FeedPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("hikari_mindful_feed_prefs", Context.MODE_PRIVATE)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun getTodayDateString(): String = dateFormat.format(Date())

    // ── Module Toggles ──────────────────────────────────────────────────────────

    private val _enabledModules = MutableStateFlow(loadEnabledModules())
    val enabledModules: StateFlow<Set<MindfulModuleType>> = _enabledModules.asStateFlow()

    private fun loadEnabledModules(): Set<MindfulModuleType> {
        val set = mutableSetOf<MindfulModuleType>()
        for (module in MindfulModuleType.entries) {
            val key = "module_enabled_${module.id}"
            if (prefs.getBoolean(key, module.defaultEnabled)) {
                set.add(module)
            }
        }
        return set
    }

    fun setModuleEnabled(module: MindfulModuleType, enabled: Boolean) {
        prefs.edit().putBoolean("module_enabled_${module.id}", enabled).apply()
        _enabledModules.value = loadEnabledModules()
    }

    // ── Module Ranks & Frequency ───────────────────────────────────────────────

    private val _moduleRanks = MutableStateFlow(loadModuleRanks())
    val moduleRanks: StateFlow<Map<MindfulModuleType, ModuleRank>> = _moduleRanks.asStateFlow()

    private fun loadModuleRanks(): Map<MindfulModuleType, ModuleRank> {
        return MindfulModuleType.entries.associateWith { module ->
            val defaultRank = when (module) {
                MindfulModuleType.LANGUAGE -> ModuleRank.RANK_3 // Standard: Stufe 3 (Top-Fokus & 6-8x täglich)
                MindfulModuleType.BRAIN_PUZZLE -> ModuleRank.RANK_2 // Erhöht (Stufe 2)
                MindfulModuleType.QUOTE -> ModuleRank.RANK_2 // Erhöht (Stufe 2)
                else -> ModuleRank.RANK_1 // Standard (Stufe 1)
            }
            val savedNumber = prefs.getInt("module_rank_${module.id}", defaultRank.rankNumber)
            // Wenn Sprache auf 1 (altes Top-Fokus) oder 3 (neues Top-Fokus) stand: immer Stufe 3
            if (module == MindfulModuleType.LANGUAGE && (savedNumber == 1 || savedNumber == 3)) {
                ModuleRank.RANK_3
            } else {
                ModuleRank.fromNumber(savedNumber)
            }
        }
    }

    fun setModuleRank(module: MindfulModuleType, rank: ModuleRank) {
        prefs.edit().putInt("module_rank_${module.id}", rank.rankNumber).apply()
        _moduleRanks.value = loadModuleRanks()
    }

    fun getModuleRank(module: MindfulModuleType): ModuleRank {
        return _moduleRanks.value[module] ?: ModuleRank.RANK_1
    }

    // ── Learning Language ───────────────────────────────────────────────────────

    private val _selectedLanguage = MutableStateFlow(loadSelectedLanguage())
    val selectedLanguage: StateFlow<LearningLanguage> = _selectedLanguage.asStateFlow()

    private fun loadSelectedLanguage(): LearningLanguage {
        val code = prefs.getString("selected_learning_language", LearningLanguage.RUSSIAN.code)
        return LearningLanguage.fromCode(code)
    }

    fun setSelectedLanguage(language: LearningLanguage) {
        prefs.edit().putString("selected_learning_language", language.code).apply()
        _selectedLanguage.value = language
    }

    // ── Daily Completion & Streak ───────────────────────────────────────────────

    private val _completedCards = MutableStateFlow(loadCompletedCards())
    val completedCards: StateFlow<Set<String>> = _completedCards.asStateFlow()

    private fun loadCompletedCards(): Set<String> {
        val today = getTodayDateString()
        val savedDate = prefs.getString("last_feed_date", "")
        if (savedDate != today) {
            // Neuer Tag -> abgeschlossene Karten des Tages leeren
            return emptySet()
        }
        return prefs.getStringSet("completed_cards_$today", emptySet()) ?: emptySet()
    }

    fun markCardCompleted(cardId: String) {
        val today = getTodayDateString()
        val current = loadCompletedCards().toMutableSet()
        current.add(cardId)

        prefs.edit()
            .putString("last_feed_date", today)
            .putStringSet("completed_cards_$today", current)
            .apply()

        _completedCards.value = current
    }

    fun resetDailyProgress() {
        val today = getTodayDateString()
        prefs.edit()
            .remove("completed_cards_$today")
            .apply()
        _completedCards.value = emptySet()
    }

    fun resetLanguageProgress() {
        prefs.edit()
            .remove("language_vocab_progress")
            .apply()
    }

    fun getStreak(): Int {
        val lastCompletedDate = prefs.getString("last_streak_completed_date", null) ?: return 0
        val today = getTodayDateString()
        if (lastCompletedDate == today) {
            return prefs.getInt("feed_streak_count", 1)
        }
        return prefs.getInt("feed_streak_count", 0)
    }

    fun incrementStreakIfEligible() {
        val today = getTodayDateString()
        val lastDate = prefs.getString("last_streak_completed_date", null)
        if (lastDate == today) return // Bereits für heute gutgeschrieben

        val currentStreak = prefs.getInt("feed_streak_count", 0)
        prefs.edit()
            .putString("last_streak_completed_date", today)
            .putInt("feed_streak_count", currentStreak + 1)
            .apply()
    }

    // ── Saved Cards ("Mein Gehirn" Wissens-Archiv) ──────────────────────────────

    private val _savedCardIds = MutableStateFlow(loadSavedCardIds())
    val savedCardIds: StateFlow<Set<String>> = _savedCardIds.asStateFlow()

    private fun loadSavedCardIds(): Set<String> {
        return prefs.getStringSet("feed_saved_cards", emptySet()) ?: emptySet()
    }

    fun toggleSavedCard(cardId: String): Boolean {
        val current = loadSavedCardIds().toMutableSet()
        val isNowSaved = if (cardId in current) {
            current.remove(cardId)
            false
        } else {
            current.add(cardId)
            true
        }
        prefs.edit().putStringSet("feed_saved_cards", current).apply()
        _savedCardIds.value = current
        return isNowSaved
    }

    fun isCardSaved(cardId: String): Boolean {
        return cardId in _savedCardIds.value
    }

    // ── Commuter Mode (Hands-Free Pendler-Modus) ─────────────────────────────────

    private val _commuterMode = MutableStateFlow(loadCommuterMode())
    val commuterMode: StateFlow<Boolean> = _commuterMode.asStateFlow()

    private fun loadCommuterMode(): Boolean {
        return prefs.getBoolean("feed_commuter_mode", false)
    }

    fun setCommuterMode(enabled: Boolean) {
        prefs.edit().putBoolean("feed_commuter_mode", enabled).apply()
        _commuterMode.value = enabled
    }

    fun toggleCommuterMode(): Boolean {
        val next = !_commuterMode.value
        setCommuterMode(next)
        return next
    }

    // ── Rotation: was wurde wann zuletzt gezeigt ────────────────────────────────

    private var lastSeenCache: MutableMap<String, Long>? = null

    /** Karten-ID → Zeitpunkt, zu dem sie zuletzt im Feed stand. */
    @Synchronized
    fun lastSeen(): Map<String, Long> = HashMap(loadLastSeen())

    private fun loadLastSeen(): MutableMap<String, Long> {
        lastSeenCache?.let { return it }
        val map = mutableMapOf<String, Long>()
        prefs.getString("feed_last_seen", null)?.split('\n')?.forEach { line ->
            val cut = line.lastIndexOf('|')
            val time = if (cut > 0) line.substring(cut + 1).toLongOrNull() else null
            if (time != null) map[line.substring(0, cut)] = time
        }
        lastSeenCache = map
        return map
    }

    @Synchronized
    fun markSeen(cardId: String) {
        val map = loadLastSeen()
        map[cardId] = System.currentTimeMillis()
        val trimmed = FeedRotation.trim(map)
        if (trimmed.size != map.size) lastSeenCache = trimmed.toMutableMap()
        prefs.edit()
            .putString("feed_last_seen", trimmed.entries.joinToString("\n") { "${it.key}|${it.value}" })
            .apply()
    }

    // ── Kyrillisches Alphabet ───────────────────────────────────────────────────

    private val _letterProgress = MutableStateFlow(loadLetterProgress())
    /** Lernstand je Buchstabe (siehe RuAlphabetLogic). */
    val letterProgress: StateFlow<Map<String, Int>> = _letterProgress.asStateFlow()

    private fun loadLetterProgress(): Map<String, Int> =
        prefs.getString("feed_letter_progress", null).orEmpty().split(',')
            .mapNotNull { part ->
                val (l, v) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                v.toIntOrNull()?.let { l to it }
            }.toMap()

    fun setLetterProgress(progress: Map<String, Int>) {
        prefs.edit()
            .putString("feed_letter_progress", progress.entries.joinToString(",") { "${it.key}:${it.value}" })
            .apply()
        _letterProgress.value = progress
    }

    // ── Runden und Übersprungenes ───────────────────────────────────────────────

    /** Fortlaufende Nummer des zuletzt zusammengestellten Schwungs — die Uhr für »kommt in drei Runden wieder«. */
    fun round(): Int = prefs.getInt("feed_round", 0)

    fun nextRound(): Int = (round() + 1).also { prefs.edit().putInt("feed_round", it).apply() }

    private var skippedCache: MutableMap<String, Pair<Int, Int>>? = null

    private fun loadSkipped(): MutableMap<String, Pair<Int, Int>> {
        skippedCache?.let { return it }
        val map = mutableMapOf<String, Pair<Int, Int>>()
        prefs.getString("feed_skipped", null)?.split('\n')?.forEach { line ->
            val parts = line.split('|')
            val count = parts.getOrNull(1)?.toIntOrNull()
            val round = parts.getOrNull(2)?.toIntOrNull()
            if (parts.size == 3 && count != null && round != null) map[parts[0]] = count to round
        }
        skippedCache = map
        return map
    }

    private fun saveSkipped(map: Map<String, Pair<Int, Int>>) {
        prefs.edit()
            .putString("feed_skipped", map.entries.joinToString("\n") { "${it.key}|${it.value.first}|${it.value.second}" })
            .apply()
    }

    /** Überwischte Karten: ID → Runde, in der sie übersprungen wurden. */
    @Synchronized
    fun skipped(): Map<String, Int> = loadSkipped().mapValues { it.value.second }

    /**
     * Eine Karte wurde nur überwischt. Beim ersten Mal kommt sie später noch einmal;
     * beim zweiten Mal gilt sie als gesehen ([FeedSkips.MAX_SKIPS]) — so staut sich nichts an.
     * Liefert true, wenn sie damit erledigt ist.
     */
    @Synchronized
    fun markSkipped(cardId: String): Boolean {
        val map = loadSkipped()
        val count = (map[cardId]?.first ?: 0) + 1
        if (count >= FeedSkips.MAX_SKIPS) {
            map.remove(cardId)
            saveSkipped(map)
            return true
        }
        map[cardId] = count to round()
        // Deckel drauf: die ältesten fliegen raus, falls jemand tausend Karten durchwischt.
        if (map.size > FeedSkips.MAX_OPEN) {
            map.entries.sortedBy { it.value.second }.take(map.size - FeedSkips.MAX_OPEN).forEach { map.remove(it.key) }
        }
        saveSkipped(map)
        return false
    }

    @Synchronized
    fun clearSkipped(cardId: String) {
        val map = loadSkipped()
        if (map.remove(cardId) != null) saveSkipped(map)
    }

    // ── Spielstufen ─────────────────────────────────────────────────────────────

    /** Schwierigkeit eines Spiels mit steigender Stufe (Zahlen merken, Reihenfolge …). */
    fun level(key: String, default: Int): Int = prefs.getInt("feed_level_$key", default)

    fun setLevel(key: String, level: Int) {
        prefs.edit().putInt("feed_level_$key", level).apply()
    }

    // ── Mustergedächtnis ────────────────────────────────────────────────────────

    /** Wie viele Felder das Mustergedächtnis gerade zeigt — steigt mit jedem Erfolg. */
    fun memoryLevel(): Int = prefs.getInt("feed_memory_level", 4)

    fun setMemoryLevel(level: Int) {
        prefs.edit().putInt("feed_memory_level", level).apply()
    }

    // ── Tagesziel ───────────────────────────────────────────────────────────────

    /** Wurde die Tagesziel-Karte heute schon gezeigt? */
    fun goalCelebratedToday(): Boolean = prefs.getString("feed_goal_celebrated", null) == getTodayDateString()

    fun markGoalCelebrated() {
        prefs.edit().putString("feed_goal_celebrated", getTodayDateString()).apply()
    }

    // ── Zeit im Feed ────────────────────────────────────────────────────────────

    /** Aktive Sekunden im Feed heute (alle Karten, nicht nur Russisch). */
    fun feedSecondsToday(): Int =
        if (prefs.getString("feed_time_date", null) == getTodayDateString()) prefs.getInt("feed_time_seconds", 0) else 0

    fun addFeedSeconds(seconds: Int) {
        prefs.edit()
            .putString("feed_time_date", getTodayDateString())
            .putInt("feed_time_seconds", feedSecondsToday() + seconds)
            .apply()
    }

    fun resetAlphabet() = setLetterProgress(emptyMap())
}
