package com.hikari.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "hikari_settings")

private val BACKEND_URL_KEY = stringPreferencesKey("backend_url")
private val SMART_DOWNLOADS_KEY = booleanPreferencesKey("smart_downloads")
private val MANUAL_RETENTION_DAYS_KEY = intPreferencesKey("manual_download_retention_days")
private val SMART_RETENTION_DAYS_KEY = intPreferencesKey("smart_download_retention_days")
private val SMART_SERIES_KEY = booleanPreferencesKey("smart_downloads_series")
private val SMART_MUSIC_KEY = booleanPreferencesKey("smart_downloads_music")
private val SMART_YOUTUBE_KEY = booleanPreferencesKey("smart_downloads_youtube")
private val SMART_BUDGET_GB_KEY = intPreferencesKey("smart_downloads_budget_gb")
private val DOWNLOADS_LIMIT_BYTES_KEY = longPreferencesKey("downloads_limit_bytes")
private val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
private val INSTRUMENTAL_ONLY_KEY = booleanPreferencesKey("music_instrumental_only")
private val MUSIC_MODE_KEY = stringPreferencesKey("music_mode")
private val NEWS_ENABLED_KEY = booleanPreferencesKey("news_enabled")
private val NEWS_TIME_MINUTES_KEY = intPreferencesKey("news_time_minutes")
private val NEWS_TOPICS_KEY = stringPreferencesKey("news_topics")
private val NEWS_LOCATION_ENABLED_KEY = booleanPreferencesKey("news_location_enabled")
private val NEWS_CITY_KEY = stringPreferencesKey("news_city")
private val NEWS_LANG_KEY = stringPreferencesKey("news_lang")
private val RUSSIAN_PODCAST_REMINDER_ENABLED_KEY = booleanPreferencesKey("russian_podcast_reminder_enabled")
private val RUSSIAN_PODCAST_REMINDER_MINUTES_KEY = intPreferencesKey("russian_podcast_reminder_minutes")

const val DEFAULT_BACKEND_URL = "http://macbook-pro.taile64a95.ts.net:3939"
/** Selbst geladene Videos bleiben, bis man sie löscht. */
const val DEFAULT_MANUAL_RETENTION_DAYS = 0
/** Smart-Downloads räumen sich nach einem Monat selbst weg. */
const val DEFAULT_SMART_RETENTION_DAYS = 30
/** So viel Speicher dürfen Smart-Downloads insgesamt belegen. */
const val DEFAULT_SMART_BUDGET_GB = 10
const val DEFAULT_NEWS_TIME_MINUTES = 450 // 7:30 Uhr
const val DEFAULT_NEWS_TOPICS = "politik,technologie,wissen"
const val DEFAULT_RUSSIAN_PODCAST_REMINDER_MINUTES = 1080

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    val backendUrl: Flow<String> = ctx.dataStore.data.map {
        it[BACKEND_URL_KEY] ?: DEFAULT_BACKEND_URL
    }

    val smartDownloads: Flow<Boolean> = ctx.dataStore.data.map {
        it[SMART_DOWNLOADS_KEY] ?: true
    }

    /** Aufbewahrung selbst geladener Videos in Tagen; 0 = für immer. */
    val manualDownloadRetentionDays: Flow<Int> = ctx.dataStore.data.map {
        it[MANUAL_RETENTION_DAYS_KEY] ?: DEFAULT_MANUAL_RETENTION_DAYS
    }

    /** Aufbewahrung der Smart-Downloads in Tagen; 0 = für immer. */
    val smartDownloadRetentionDays: Flow<Int> = ctx.dataStore.data.map {
        it[SMART_RETENTION_DAYS_KEY] ?: DEFAULT_SMART_RETENTION_DAYS
    }

    // Smart-Downloads je Art getrennt schaltbar. Serien/Filme und Musik liefen
    // schon bisher mit, YouTube ist neu und deshalb zunächst aus.
    val smartSeries: Flow<Boolean> = ctx.dataStore.data.map { it[SMART_SERIES_KEY] ?: true }
    val smartMusic: Flow<Boolean> = ctx.dataStore.data.map { it[SMART_MUSIC_KEY] ?: true }
    val smartYoutube: Flow<Boolean> = ctx.dataStore.data.map { it[SMART_YOUTUBE_KEY] ?: false }

    /** Speicherbudget der Smart-Downloads in GB. */
    val smartBudgetGb: Flow<Int> = ctx.dataStore.data.map { it[SMART_BUDGET_GB_KEY] ?: DEFAULT_SMART_BUDGET_GB }

    /** Letztes vom Server gemeldetes Storage-Limit. Wird offline weitergeführt. */
    val downloadsLimitBytes: Flow<Long> = ctx.dataStore.data.map {
        it[DOWNLOADS_LIMIT_BYTES_KEY] ?: 0L
    }

    /**
     * Optionaler Bearer-Token. Wird bei jeder Anfrage mitgeschickt, wenn der
     * Server HIKARI_AUTH_TOKEN gesetzt hat. Leer = kein Token (localhost-Default).
     */
    val authToken: Flow<String> = ctx.dataStore.data.map {
        it[AUTH_TOKEN_KEY].orEmpty()
    }

    /**
     * Gewählter Hör-Modus des Musikbereichs (MusicSearchMode.apiValue). Der
     * alte "Ohne Gesang"-Schalter ist im Modus "instrumental" aufgegangen —
     * wer ihn an hatte, landet beim ersten Start direkt dort.
     */
    val musicMode: Flow<String> = ctx.dataStore.data.map {
        it[MUSIC_MODE_KEY] ?: if (it[INSTRUMENTAL_ONLY_KEY] == true) "instrumental" else "music"
    }

    /** Täglicher News-Tagesbericht: Push-Benachrichtigung aktiv. */
    val newsEnabled: Flow<Boolean> = ctx.dataStore.data.map {
        it[NEWS_ENABLED_KEY] ?: false
    }

    /** Uhrzeit des Tagesberichts in Minuten seit Mitternacht (Default 7:30). */
    val newsTimeMinutes: Flow<Int> = ctx.dataStore.data.map {
        it[NEWS_TIME_MINUTES_KEY] ?: DEFAULT_NEWS_TIME_MINUTES
    }

    val russianPodcastReminderEnabled: Flow<Boolean> = ctx.dataStore.data.map {
        it[RUSSIAN_PODCAST_REMINDER_ENABLED_KEY] ?: false
    }

    val russianPodcastReminderMinutes: Flow<Int> = ctx.dataStore.data.map {
        it[RUSSIAN_PODCAST_REMINDER_MINUTES_KEY] ?: DEFAULT_RUSSIAN_PODCAST_REMINDER_MINUTES
    }

    /** Gewählte News-Themen — gespeichert als kommagetrennter String. */
    val newsTopics: Flow<Set<String>> = ctx.dataStore.data.map {
        (it[NEWS_TOPICS_KEY] ?: DEFAULT_NEWS_TOPICS)
            .split(",")
            .map { t -> t.trim() }
            .filter { t -> t.isNotEmpty() }
            .toSet()
    }

    /** Standort für lokale Nachrichten + Sprache ableiten. */
    val newsLocationEnabled: Flow<Boolean> = ctx.dataStore.data.map {
        it[NEWS_LOCATION_ENABLED_KEY] ?: false
    }

    /** Zuletzt ermittelte (oder manuell gesetzte) Stadt für lokale Nachrichten. */
    val newsCity: Flow<String> = ctx.dataStore.data.map {
        it[NEWS_CITY_KEY].orEmpty()
    }

    /** Sprache des Tagesberichts ("de" | "en"). */
    val newsLang: Flow<String> = ctx.dataStore.data.map {
        it[NEWS_LANG_KEY] ?: "de"
    }

    suspend fun setNewsEnabled(enabled: Boolean) {
        ctx.dataStore.edit { it[NEWS_ENABLED_KEY] = enabled }
    }

    suspend fun setNewsTimeMinutes(minutes: Int) {
        ctx.dataStore.edit { it[NEWS_TIME_MINUTES_KEY] = minutes.coerceIn(0, 1439) }
    }

    suspend fun setRussianPodcastReminderEnabled(enabled: Boolean) {
        ctx.dataStore.edit { it[RUSSIAN_PODCAST_REMINDER_ENABLED_KEY] = enabled }
    }

    suspend fun setRussianPodcastReminderMinutes(minutes: Int) {
        ctx.dataStore.edit { it[RUSSIAN_PODCAST_REMINDER_MINUTES_KEY] = minutes.coerceIn(0, 1439) }
    }

    suspend fun setNewsTopics(topics: Set<String>) {
        ctx.dataStore.edit {
            it[NEWS_TOPICS_KEY] = topics
                .map { t -> t.trim().lowercase() }
                .filter { t -> t.isNotEmpty() }
                .joinToString(",")
        }
    }

    suspend fun setNewsLocationEnabled(enabled: Boolean) {
        ctx.dataStore.edit { it[NEWS_LOCATION_ENABLED_KEY] = enabled }
    }

    suspend fun setNewsCity(city: String) {
        ctx.dataStore.edit { it[NEWS_CITY_KEY] = city.trim() }
    }

    suspend fun setNewsLang(lang: String) {
        ctx.dataStore.edit { it[NEWS_LANG_KEY] = lang }
    }

    suspend fun setMusicMode(mode: String) {
        ctx.dataStore.edit { it[MUSIC_MODE_KEY] = mode }
    }

    suspend fun setBackendUrl(url: String) {
        ctx.dataStore.edit { it[BACKEND_URL_KEY] = url.trimEnd('/') }
    }

    suspend fun setManualDownloadRetentionDays(days: Int) {
        ctx.dataStore.edit { it[MANUAL_RETENTION_DAYS_KEY] = days.coerceAtLeast(0) }
    }

    suspend fun setSmartSeries(enabled: Boolean) {
        ctx.dataStore.edit { it[SMART_SERIES_KEY] = enabled }
    }

    suspend fun setSmartMusic(enabled: Boolean) {
        ctx.dataStore.edit { it[SMART_MUSIC_KEY] = enabled }
    }

    suspend fun setSmartYoutube(enabled: Boolean) {
        ctx.dataStore.edit { it[SMART_YOUTUBE_KEY] = enabled }
    }

    suspend fun setSmartBudgetGb(gb: Int) {
        ctx.dataStore.edit { it[SMART_BUDGET_GB_KEY] = gb.coerceAtLeast(1) }
    }

    suspend fun setSmartDownloadRetentionDays(days: Int) {
        ctx.dataStore.edit { it[SMART_RETENTION_DAYS_KEY] = days.coerceAtLeast(0) }
    }

    suspend fun setSmartDownloads(enabled: Boolean) {
        ctx.dataStore.edit { it[SMART_DOWNLOADS_KEY] = enabled }
    }

    suspend fun setDownloadsLimitBytes(bytes: Long) {
        ctx.dataStore.edit { it[DOWNLOADS_LIMIT_BYTES_KEY] = bytes }
    }

    suspend fun setAuthToken(token: String) {
        ctx.dataStore.edit {
            val t = token.trim()
            if (t.isEmpty()) it.remove(AUTH_TOKEN_KEY) else it[AUTH_TOKEN_KEY] = t
        }
    }
}
