package com.hikari.app.ui.news

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.NewsTopicDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.model.NewsItem
import com.hikari.app.domain.news.NewsBriefingScheduler
import com.hikari.app.domain.news.NewsLocationHelper
import com.hikari.app.domain.repo.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val repo: NewsRepository,
    private val settings: SettingsStore,
    private val scheduler: NewsBriefingScheduler,
    private val locationHelper: NewsLocationHelper,
) : ViewModel() {

    var items by mutableStateOf<List<NewsItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var failed by mutableStateOf(false)
        private set

    /** Ob das Einstellungs-Sheet offen ist. */
    var settingsOpen by mutableStateOf(false)

    // --- Snapshot der News-Settings fürs Sheet ---
    var newsEnabled by mutableStateOf(false)
        private set
    var newsTimeMinutes by mutableStateOf(450)
        private set
    var newsTopicsSelected by mutableStateOf<Set<String>>(emptySet())
        private set
    var newsLocationEnabled by mutableStateOf(false)
        private set
    var newsCity by mutableStateOf("")
        private set
    var newsLang by mutableStateOf("de")
        private set

    /** Vom Backend angebotene Themen fürs Sheet — leer, wenn der Server keine liefert. */
    var availableTopics by mutableStateOf<List<NewsTopicDto>>(emptyList())
        private set

    /** true, während der Standort gerade ermittelt wird. */
    var locationResolving by mutableStateOf(false)
        private set

    init {
        reload(force = false)
    }

    fun reload(force: Boolean) {
        viewModelScope.launch {
            loading = true
            failed = false
            try {
                items = repo.getBriefing(force)
            } catch (_: Exception) {
                failed = true
            }
            loading = false
        }
    }

    /** Öffnet das Sheet und lädt den aktuellen Settings-Stand + Themenliste. */
    fun openSettings() {
        settingsOpen = true
        viewModelScope.launch {
            newsEnabled = settings.newsEnabled.first()
            newsTimeMinutes = settings.newsTimeMinutes.first()
            newsTopicsSelected = settings.newsTopics.first()
            newsLocationEnabled = settings.newsLocationEnabled.first()
            newsCity = settings.newsCity.first()
            newsLang = settings.newsLang.first()
        }
        viewModelScope.launch {
            availableTopics = runCatching { repo.getTopics() }.getOrDefault(emptyList())
        }
    }

    fun closeSettings() {
        settingsOpen = false
    }

    fun toggleTopic(key: String) {
        newsTopicsSelected = if (key in newsTopicsSelected) {
            newsTopicsSelected - key
        } else {
            newsTopicsSelected + key
        }
    }

    /** Eigenes Thema aus dem Freitext-Feld übernehmen. */
    fun addCustomTopic(raw: String) {
        val key = raw.trim().lowercase()
        if (key.isNotEmpty()) newsTopicsSelected = newsTopicsSelected + key
    }

    fun onNewsEnabledChange(enabled: Boolean) {
        newsEnabled = enabled
    }

    fun setTimeMinutes(minutes: Int) {
        newsTimeMinutes = minutes.coerceIn(0, 1439)
    }

    fun setManualCity(city: String) {
        newsCity = city
    }

    fun setLocationEnabled(enabled: Boolean) {
        newsLocationEnabled = enabled
    }

    /**
     * Nach gewährter Standort-Permission: Stadt + Sprache ermitteln und im
     * Settings-Snapshot spiegeln. Läuft still fehl — der Toggle bleibt an,
     * das manuelle Ortsfeld ist der Fallback.
     */
    fun resolveLocation() {
        viewModelScope.launch {
            locationResolving = true
            locationHelper.resolve()?.let { loc ->
                settings.setNewsCity(loc.city)
                settings.setNewsLang(loc.lang)
                newsCity = loc.city
                newsLang = loc.lang
            }
            locationResolving = false
        }
    }

    /** Speichert alles, plant den Worker um und lädt den Feed mit force neu. */
    fun saveSettings() {
        viewModelScope.launch {
            settings.setNewsEnabled(newsEnabled)
            settings.setNewsTimeMinutes(newsTimeMinutes)
            settings.setNewsTopics(newsTopicsSelected)
            settings.setNewsLocationEnabled(newsLocationEnabled)
            settings.setNewsCity(newsCity)
            settings.setNewsLang(newsLang)
            if (newsEnabled) {
                scheduler.schedule(newsTimeMinutes)
            } else {
                scheduler.cancel()
            }
            settingsOpen = false
            reload(force = true)
        }
    }
}
