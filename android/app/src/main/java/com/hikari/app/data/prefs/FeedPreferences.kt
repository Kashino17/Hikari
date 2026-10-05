package com.hikari.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.hikari.app.domain.feed.LearningLanguage
import com.hikari.app.domain.feed.MindfulModuleType
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
            // Neuer Tag ➔ abgeschlossene Karten des Tages leeren
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
}
